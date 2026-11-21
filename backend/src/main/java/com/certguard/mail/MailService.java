package com.certguard.mail;

import com.certguard.config.AppProperties;
import com.certguard.entity.Certificate;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final JavaMailSender sender;
    private final TemplateEngine templates;
    private final AppProperties props;
    private final String username;
    private final String password;

    public MailService(JavaMailSender sender, TemplateEngine templates, AppProperties props,
                       @Value("${spring.mail.username:}") String username,
                       @Value("${spring.mail.password:}") String password) {
        this.sender = sender;
        this.templates = templates;
        this.props = props;
        this.username = username;
        this.password = password;
    }

    public boolean isConfigured() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }

    public String senderAddress() {
        return username;
    }

    /** Result of a send with retries. */
    public record SendOutcome(boolean sent, int attempts, String error) {}

    public SendOutcome sendExpiryReminder(Certificate c, long daysLeft, String to) {
        String subject;
        if (daysLeft > 0) {
            subject = "Reminder: \"" + c.getTitle() + "\" expires in " + daysLeft + " day" + (daysLeft == 1 ? "" : "s")
                    + " (" + DATE.format(c.getExpiryDate()) + ")";
        } else if (daysLeft == 0) {
            subject = "Expires TODAY: \"" + c.getTitle() + "\"";
        } else {
            subject = "EXPIRED: \"" + c.getTitle() + "\" expired " + (-daysLeft) + " day" + (daysLeft == -1 ? "" : "s")
                    + " ago";
        }

        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("c", c);
        ctx.setVariable("daysLeft", daysLeft);
        ctx.setVariable("absDays", Math.abs(daysLeft));
        ctx.setVariable("issueDate", c.getIssueDate() == null ? "-" : DATE.format(c.getIssueDate()));
        ctx.setVariable("expiryDate", DATE.format(c.getExpiryDate()));
        ctx.setVariable("type", c.getType().name().replace('_', ' '));
        ctx.setVariable("link", props.frontendUrl() + "/certificates/" + c.getId());
        String html = templates.process("expiry-reminder", ctx);
        return sendWithRetry(to, subject, html);
    }

    public SendOutcome sendTest(String to) {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("link", props.frontendUrl());
        String html = templates.process("test-email", ctx);
        return sendWithRetry(to, "CertGuard test email", html);
    }

    private SendOutcome sendWithRetry(String to, String subject, String html) {
        if (!isConfigured()) {
            return new SendOutcome(false, 0, "Mail is not configured: set MAIL_USERNAME and MAIL_PASSWORD (Gmail App Password)");
        }
        int max = Math.max(1, props.mail().maxAttempts());
        String lastError = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            try {
                MimeMessage msg = sender.createMimeMessage();
                MimeMessageHelper h = new MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name());
                h.setFrom(new InternetAddress(username, props.mail().fromName(), StandardCharsets.UTF_8.name()));
                h.setTo(to);
                h.setSubject(subject);
                h.setText(html, true);
                sender.send(msg);
                log.info("Mail sent to {} (attempt {}): {}", to, attempt, subject);
                return new SendOutcome(true, attempt, null);
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.warn("Mail to {} failed (attempt {}/{}): {}", to, attempt, max, lastError);
                if (attempt < max) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        if (lastError != null && lastError.length() > 990) lastError = lastError.substring(0, 990);
        return new SendOutcome(false, max, lastError);
    }
}
