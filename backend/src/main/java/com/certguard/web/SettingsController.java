package com.certguard.web;

import com.certguard.dto.SettingsDtos.SettingsResponse;
import com.certguard.dto.SettingsDtos.SettingsUpdate;
import com.certguard.mail.MailService;
import com.certguard.service.CurrentUser;
import com.certguard.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settings;
    private final MailService mail;
    private final CurrentUser me;

    public SettingsController(SettingsService settings, MailService mail, CurrentUser me) {
        this.settings = settings;
        this.mail = mail;
        this.me = me;
    }

    @GetMapping
    public SettingsResponse get() {
        return settings.get(me.id());
    }

    @PutMapping
    public SettingsResponse update(@Valid @RequestBody SettingsUpdate req) {
        return settings.update(me.id(), req);
    }

    @PostMapping("/test-email")
    public Map<String, Object> testEmail() {
        String to = settings.notifyEmail(me.id());
        MailService.SendOutcome out = mail.sendTest(to);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sent", out.sent());
        r.put("to", to);
        r.put("attempts", out.attempts());
        r.put("error", out.error());
        return r;
    }
}
