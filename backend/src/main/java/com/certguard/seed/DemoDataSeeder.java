package com.certguard.seed;

import com.certguard.config.AppProperties;
import com.certguard.entity.*;
import com.certguard.repository.CertificateFileRepository;
import com.certguard.repository.CertificateRepository;
import com.certguard.repository.UserRepository;
import com.certguard.service.ExpiryCalculator;
import com.certguard.service.ReminderDays;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Creates a demo account with 5 certificates in different expiry states (only on an empty database,
 * only when SEED_DEMO=true). Each one gets a generated PDF so preview, download and extraction can be
 * tried out. Reminders are OFF on demo certificates so no email goes to the fake demo address.
 */
@Component
@Order(1)
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_EMAIL = "demo@certguard.local";
    public static final String DEMO_PASSWORD = "Demo@1234";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final DateTimeFormatter PDF_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);

    private final AppProperties props;
    private final UserRepository users;
    private final CertificateRepository certificates;
    private final CertificateFileRepository files;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public DemoDataSeeder(AppProperties props, UserRepository users, CertificateRepository certificates,
                          CertificateFileRepository files, PasswordEncoder encoder, Clock clock) {
        this.props = props;
        this.users = users;
        this.certificates = certificates;
        this.files = files;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!props.seedDemo() || users.count() > 0) return;
        LocalDate t = LocalDate.now(clock);

        User u = new User();
        u.setName("Demo User");
        u.setEmail(DEMO_EMAIL);
        u.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        u.setRole(Role.USER);
        u.setDefaultReminderDays("30,15,7,1");
        users.save(u);

        add(u, "ISO 9001:2015 Quality Management", CertType.ISO, "Bureau of Quality Certification",
                "QMS-2024-00917", t.minusDays(695), t.plusDays(400));
        add(u, "Wildcard SSL - *.example.internal", CertType.SSL_TLS, "Internal Root CA",
                "SSL-7F3A-2291", t.minusDays(345), t.plusDays(20));
        add(u, "Fire Safety Licence - Plant 2", CertType.LICENSE, "State Fire & Emergency Services",
                "FSL/TN/2025/4471", t.minusDays(360), t.plusDays(5));
        add(u, "Forklift Operator Training", CertType.TRAINING, "SafeLift Training Institute",
                "FOT-88213", t.minusDays(740), t.minusDays(10));
        add(u, "Fleet Vehicle Insurance", CertType.INSURANCE, "National General Insurance Co.",
                "POL-552-118-90", t.minusDays(365), t);
        log.info("Seeded demo account {} / {} with 5 certificates", DEMO_EMAIL, DEMO_PASSWORD);
    }

    private void add(User u, String title, CertType type, String issuer, String number, LocalDate issued,
                     LocalDate expires) throws Exception {
        Certificate c = new Certificate();
        c.setUser(u);
        c.setTitle(title);
        c.setType(type);
        c.setIssuer(issuer);
        c.setCertNumber(number);
        c.setIssueDate(issued);
        c.setExpiryDate(expires);
        c.setNotifyEmail(u.getEmail());
        c.setDateSource(DateSource.AUTO);
        c.setRemindersEnabled(false);
        c.setTags("demo");
        c.setNotes("Sample record created by the demo seed.");
        c.setStatus(ExpiryCalculator.status(ExpiryCalculator.daysLeft(expires, LocalDate.now(clock)),
                ReminderDays.parse(u.getDefaultReminderDays())));
        certificates.save(c);

        byte[] pdf = pdf(title, issuer, number, issued, expires);
        CertificateFile f = new CertificateFile();
        f.setCertificate(c);
        f.setFileName(title.replaceAll("[^A-Za-z0-9]+", "_") + ".pdf");
        f.setMimeType("application/pdf");
        f.setSizeBytes(pdf.length);
        f.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)));
        f.setData(pdf);
        f.setVersion(1);
        files.save(f);
    }

    static byte[] pdf(String title, String issuer, String number, LocalDate issued, LocalDate expires)
            throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            doc.addPage(page);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float w = page.getMediaBox().getWidth();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setLineWidth(3);
                cs.addRect(30, 30, w - 60, page.getMediaBox().getHeight() - 60);
                cs.stroke();
                line(cs, bold, 30, 70, 470, "CERTIFICATE");
                line(cs, bold, 20, 70, 420, title);
                line(cs, regular, 14, 70, 370, "Issued by: " + issuer);
                line(cs, regular, 14, 70, 340, "Certificate No: " + number);
                line(cs, regular, 14, 70, 310, "Date of Issue: " + PDF_DATE.format(issued));
                line(cs, regular, 14, 70, 280, "Valid Until: " + PDF_DATE.format(expires));
                line(cs, regular, 10, 70, 80, "Demo document generated by CertGuard. Not a real certificate.");
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void line(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String text)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }
}
