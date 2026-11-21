package com.certguard.web;

import com.certguard.config.AppUserPrincipal;
import com.certguard.dto.ExpiryCheckResult;
import com.certguard.extraction.OcrService;
import com.certguard.mail.MailService;
import com.certguard.scheduler.ExpiryCheckService;
import com.certguard.service.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AdminController {

    private final ExpiryCheckService expiryCheck;
    private final CurrentUser me;
    private final MailService mail;
    private final OcrService ocr;

    public AdminController(ExpiryCheckService expiryCheck, CurrentUser me, MailService mail, OcrService ocr) {
        this.expiryCheck = expiryCheck;
        this.me = me;
        this.mail = mail;
        this.ocr = ocr;
    }

    /** "Run check now": admins run it for everyone, other users for their own certificates. */
    @PostMapping("/api/admin/run-expiry-check")
    public ExpiryCheckResult runNow() {
        AppUserPrincipal p = me.get();
        return expiryCheck.run(p.isAdmin() ? null : p.id());
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "mailConfigured", mail.isConfigured(), "ocrAvailable", ocr.isAvailable());
    }
}
