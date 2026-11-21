package com.certguard.service;

import com.certguard.config.AppProperties;
import com.certguard.dto.SettingsDtos.SettingsResponse;
import com.certguard.dto.SettingsDtos.SettingsUpdate;
import com.certguard.entity.User;
import com.certguard.mail.MailService;
import com.certguard.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SettingsService {

    private final UserRepository users;
    private final AppProperties props;
    private final MailService mail;

    public SettingsService(UserRepository users, AppProperties props, MailService mail) {
        this.users = users;
        this.props = props;
        this.mail = mail;
    }

    @Transactional(readOnly = true)
    public SettingsResponse get(Long userId) {
        return toResponse(load(userId));
    }

    @Transactional
    public SettingsResponse update(Long userId, SettingsUpdate req) {
        User u = load(userId);
        List<Integer> days = ReminderDays.normalize(req.defaultReminderDays());
        if (days.isEmpty()) throw ApiException.badRequest("Reminder days must be between 0 and 3650");
        u.setName(req.name().trim());
        u.setNotifyEmail(req.notifyEmail() == null || req.notifyEmail().isBlank() ? null : req.notifyEmail().trim());
        u.setDefaultReminderDays(ReminderDays.format(days));
        users.save(u);
        return toResponse(u);
    }

    @Transactional(readOnly = true)
    public String notifyEmail(Long userId) {
        return load(userId).effectiveNotifyEmail();
    }

    private User load(Long userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }

    private SettingsResponse toResponse(User u) {
        return new SettingsResponse(u.getName(), u.getEmail(), u.effectiveNotifyEmail(),
                ReminderDays.parse(u.getDefaultReminderDays()), props.expiryCheck().cron(), props.timezone(),
                mail.isConfigured(), mail.senderAddress());
    }
}
