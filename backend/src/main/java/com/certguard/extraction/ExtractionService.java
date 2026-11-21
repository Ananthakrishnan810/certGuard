package com.certguard.extraction;

import com.certguard.dto.ExtractionResult;
import com.certguard.entity.CertType;
import com.certguard.service.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);
    private static final int MAX_OCR_PAGES = 3;

    private final OcrService ocr;
    private final ZoneId zone;

    public ExtractionService(OcrService ocr, java.time.Clock clock) {
        this.ocr = ocr;
        this.zone = clock.getZone();
    }

    public ExtractionResult extract(byte[] bytes, String fileName, String keystorePassword) {
        FileKind kind = FileTypeDetector.validate(fileName, bytes);
        return switch (kind) {
            case PEM, ASN1 -> extractCertificateFile(bytes, fileName, kind, keystorePassword);
            case PDF -> extractPdf(bytes, fileName);
            case PNG, JPEG -> fromText(fileName, "IMAGE",
                    ocr.ocr(bytes, kind == FileKind.PNG ? ".png" : ".jpg"), "OCR");
        };
    }

    // ---------------------------------------------------------------- X.509 / PKCS#12

    private ExtractionResult extractCertificateFile(byte[] bytes, String fileName, FileKind kind, String password) {
        if (FileTypeDetector.isKeystore(fileName)) {
            return extractPkcs12(bytes, password);
        }
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            // a PEM may contain a chain; the first certificate is the leaf
            var certs = cf.generateCertificates(new ByteArrayInputStream(bytes));
            if (certs.isEmpty()) throw ApiException.badRequest("No certificate found in file");
            return fromX509((X509Certificate) certs.iterator().next(), "X509");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            if (kind == FileKind.ASN1) {
                // DER that is not X.509 may be a keystore saved with a .cer/.crt name
                return extractPkcs12(bytes, password);
            }
            throw ApiException.badRequest("Could not read certificate: " + e.getMessage());
        }
    }

    private ExtractionResult extractPkcs12(byte[] bytes, String password) {
        if (password == null) {
            return new ExtractionResult("PKCS12", "NONE", false, true, null, CertType.SSL_TLS, null, null, null,
                    null, "This keystore is password protected. Enter its password to read the dates.", null);
        }
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(bytes), password.toCharArray());
            for (String alias : Collections.list(ks.aliases())) {
                if (ks.getCertificate(alias) instanceof X509Certificate x) {
                    ExtractionResult r = fromX509(x, "PKCS12");
                    return r;
                }
            }
            throw ApiException.badRequest("Keystore contains no X.509 certificate");
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            return new ExtractionResult("PKCS12", "NONE", false, true, null, CertType.SSL_TLS, null, null, null,
                    null, "Wrong keystore password, or the file is not a valid PKCS#12 keystore.", null);
        } catch (Exception e) {
            throw ApiException.badRequest("Could not read keystore: " + e.getMessage());
        }
    }

    private ExtractionResult fromX509(X509Certificate x, String kind) {
        LocalDate issued = x.getNotBefore().toInstant().atZone(zone).toLocalDate();
        LocalDate expires = x.getNotAfter().toInstant().atZone(zone).toLocalDate();
        String subject = rdn(x.getSubjectX500Principal().getName(), "CN");
        if (subject == null) subject = rdn(x.getSubjectX500Principal().getName(), "O");
        String issuer = rdn(x.getIssuerX500Principal().getName(), "O");
        String issuerCn = rdn(x.getIssuerX500Principal().getName(), "CN");
        if (issuer == null) issuer = issuerCn;
        else if (issuerCn != null && !issuerCn.equals(issuer)) issuer = issuer + " (" + issuerCn + ")";
        return new ExtractionResult(kind, "X509", true, false,
                subject != null ? subject : "Certificate",
                CertType.SSL_TLS, issuer, x.getSerialNumber().toString(16).toUpperCase(Locale.ROOT),
                issued, expires,
                "Dates read from the certificate (notBefore / notAfter).",
                "Subject: " + x.getSubjectX500Principal().getName() + "\nIssuer: " + x.getIssuerX500Principal().getName());
    }

    private static String rdn(String dn, String type) {
        try {
            for (Rdn r : new LdapName(dn).getRdns()) {
                if (r.getType().equalsIgnoreCase(type)) return String.valueOf(r.getValue());
            }
        } catch (InvalidNameException ignored) {
        }
        return null;
    }

    // ---------------------------------------------------------------- PDF / images

    private ExtractionResult extractPdf(byte[] bytes, String fileName) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(doc);
            if (text != null && text.replaceAll("\\s", "").length() >= 20) {
                return fromText(fileName, "PDF", Optional.of(text), "PDF_TEXT");
            }
            // scanned PDF: render pages and OCR them
            PDFRenderer renderer = new PDFRenderer(doc);
            List<BufferedImage> pages = new ArrayList<>();
            for (int i = 0; i < Math.min(doc.getNumberOfPages(), MAX_OCR_PAGES); i++) {
                pages.add(renderer.renderImageWithDPI(i, 300, ImageType.GRAY));
            }
            return fromText(fileName, "PDF", ocr.ocr(pages), "OCR");
        } catch (IOException e) {
            log.warn("PDF parse failed: {}", e.getMessage());
            throw ApiException.badRequest("Could not read PDF: " + e.getMessage());
        }
    }

    private ExtractionResult fromText(String fileName, String fileKind, Optional<String> text, String method) {
        String title = prettyTitle(fileName);
        if (text.isEmpty()) {
            String why = method.equals("OCR") && !ocr.isAvailable()
                    ? "OCR is not installed on the server, so dates could not be read from this scan."
                    : "No readable text found in the file.";
            return new ExtractionResult(fileKind, "NONE", false, false, title, CertType.OTHER, null, null, null, null,
                    why + " Please enter the dates manually.", null);
        }
        String t = text.get();
        TextDateExtractor.Result r = TextDateExtractor.extract(t);
        boolean found = r.expiryDate() != null;
        String msg;
        if (!found) {
            msg = "Could not find an expiry date in the document. Please enter it manually.";
        } else if (!r.labelled()) {
            msg = "No date labels found; guessed earliest date as issue and latest as expiry. Please check.";
        } else {
            msg = "Dates read from the document text" + (method.equals("OCR") ? " (OCR)" : "") + ". Please confirm.";
        }
        String snippet = t.replaceAll("[ \\t]+", " ").replaceAll("\\n{2,}", "\n").trim();
        if (snippet.length() > 600) snippet = snippet.substring(0, 600) + "...";
        return new ExtractionResult(fileKind, found ? method : "NONE", found, false, title, guessType(t),
                r.issuer(), r.certNumber(), r.issueDate(), r.expiryDate(), msg, snippet);
    }

    static CertType guessType(String text) {
        String s = text.toLowerCase(Locale.ROOT);
        if (s.matches("(?s).*\\biso\\s*\\d{3,5}.*")) return CertType.ISO;
        if (s.contains("insurance") || s.contains("policy no") || s.contains("insured")) return CertType.INSURANCE;
        if (s.contains("licence") || s.contains("license")) return CertType.LICENSE;
        if (s.contains("training") || s.contains("course") || s.contains("completion")) return CertType.TRAINING;
        if (s.contains("ssl") || s.contains("tls")) return CertType.SSL_TLS;
        return CertType.OTHER;
    }

    static String prettyTitle(String fileName) {
        if (fileName == null || fileName.isBlank()) return "Certificate";
        String base = fileName.replaceAll("\\.[^.]+$", "").replaceAll("[_\\-]+", " ").trim();
        return base.isEmpty() ? "Certificate" : Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }
}
