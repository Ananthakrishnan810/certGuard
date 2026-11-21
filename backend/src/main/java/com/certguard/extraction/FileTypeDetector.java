package com.certguard.extraction;

import com.certguard.service.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FileTypeDetector {

    private static final Map<String, Set<FileKind>> ALLOWED = Map.of(
            "pdf", Set.of(FileKind.PDF),
            "png", Set.of(FileKind.PNG),
            "jpg", Set.of(FileKind.JPEG),
            "jpeg", Set.of(FileKind.JPEG),
            "pem", Set.of(FileKind.PEM),
            "crt", Set.of(FileKind.PEM, FileKind.ASN1),
            "cer", Set.of(FileKind.PEM, FileKind.ASN1),
            "der", Set.of(FileKind.ASN1),
            "p12", Set.of(FileKind.ASN1),
            "pfx", Set.of(FileKind.ASN1));

    private FileTypeDetector() {}

    public static String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isKeystore(String fileName) {
        String ext = extension(fileName);
        return ext.equals("p12") || ext.equals("pfx");
    }

    public static FileKind detect(byte[] b) {
        if (b == null || b.length < 4) return null;
        if (b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return FileKind.PDF;
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return FileKind.PNG;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return FileKind.JPEG;
        String head = new String(b, 0, Math.min(b.length, 200), StandardCharsets.US_ASCII).stripLeading();
        if (head.startsWith("-----BEGIN")) return FileKind.PEM;
        if ((b[0] & 0xFF) == 0x30) return FileKind.ASN1; // DER SEQUENCE: X.509 or PKCS#12
        return null;
    }

    /** Checks extension is supported and agrees with the content. Returns the detected kind. */
    public static FileKind validate(String fileName, byte[] bytes) {
        String ext = extension(fileName);
        Set<FileKind> allowed = ALLOWED.get(ext);
        if (allowed == null) {
            throw ApiException.badRequest("Unsupported file type '." + ext
                    + "'. Allowed: PDF, PNG, JPG, PEM, CRT, CER, DER, P12, PFX");
        }
        FileKind kind = detect(bytes);
        if (kind == null || !allowed.contains(kind)) {
            throw ApiException.badRequest("File content does not match its ." + ext + " extension");
        }
        return kind;
    }

    public static String mimeType(FileKind kind, String fileName) {
        return switch (kind) {
            case PDF -> "application/pdf";
            case PNG -> "image/png";
            case JPEG -> "image/jpeg";
            case PEM -> "application/x-pem-file";
            case ASN1 -> isKeystore(fileName) ? "application/x-pkcs12" : "application/pkix-cert";
        };
    }
}
