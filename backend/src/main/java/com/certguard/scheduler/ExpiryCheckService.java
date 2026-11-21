package com.certguard.scheduler;

import com.certguard.dto.ExpiryCheckResult;
import com.certguard.entity.CertStatus;
import com.certguard.entity.Certificate;
import com.certguard.entity.NotificationLog;
import com.certguard.entity.NotificationStatus;
import com.certguard.mail.MailService;
import com.certguard.repository.CertificateRepository;
import com.certguard.repository.NotificationLogRepository;
import com.certguard.service.ExpiryCalculator;
import com.certguard.service.ReminderDays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ExpiryCheckService {

    private static final Logger log = LoggerFactory.getLogger(ExpiryCheckService.class);

    private final CertificateRepository certificates;
    private final NotificationLogRepository notifications;
    private final MailService mail;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    public ExpiryCheckService(CertificateRepository certificates, NotificationLogRepository notifications,
                              MailService mail, Clock clock) {
        this.certificates = certificates;
        this.notifications = notifications;
        this.mail = mail;
        this.clock = clock;
    }

    /** Recompute statuses without sending email. Cheap; runs at startup and hourly. */
    public int refreshStatuses() {
        LocalDate today = LocalDate.now(clock);
        int changed = 0;
        for (Certificate c : certificates.findAllActiveWithUser()) {
            if (updateStatus(c, today)) changed++;
        }
        return changed;
    }

    /**
     * Full check: update statuses and send due reminders.
     *
     * @param userId only this user's certificates, or null for everyone
     */
    public ExpiryCheckResult run(Long userId) {
        lock.lock(); // one run at a time inside this instance; the dedup_key unique index covers the rest
        try {
            LocalDate today = LocalDate.now(clock);
            List<Certificate> list = userId == null
                    ? certificates.findAllActiveWithUser()
                    : certificates.findActiveWithUserByUserId(userId);

            int changed = 0, sent = 0, failed = 0, skipped = 0;
            for (Certificate c : list) {
                if (updateStatus(c, today)) changed++;
                if (!c.isRemindersEnabled()) {
                    skipped++;
                    continue;
                }
                long daysLeft = ExpiryCalculator.daysLeft(c.getExpiryDate(), today);
                Optional<Integer> due = ExpiryCalculator.dueReminderKey(daysLeft, ReminderDays.effective(c));
                if (due.isEmpty()) continue;

                String key = NotificationLog.dedupKey(c.getId(), c.getExpiryDate(), due.get());
                if (notifications.existsByDedupKey(key)) continue;

                String to = (c.getNotifyEmail() != null && !c.getNotifyEmail().isBlank())
                        ? c.getNotifyEmail()
                        : c.getUser().effectiveNotifyEmail();
                MailService.SendOutcome out = mail.sendExpiryReminder(c, daysLeft, to);

                NotificationLog n = new NotificationLog();
                n.setCertificate(c);
                n.setOffsetDays(due.get());
                n.setExpiryDate(c.getExpiryDate());
                n.setSentTo(to);
                n.setSentAt(Instant.now());
                n.setAttempts(out.attempts());
                n.setStatus(out.sent() ? NotificationStatus.SENT : NotificationStatus.FAILED);
                n.setError(out.error());
                // FAILED rows keep dedup_key NULL so the next run retries them
                n.setDedupKey(out.sent() ? key : null);
                try {
                    notifications.save(n);
                } catch (DataIntegrityViolationException dup) {
                    log.info("Reminder {} already recorded by another run", key);
                }
                if (out.sent()) sent++; else failed++;
            }
            ExpiryCheckResult r = new ExpiryCheckResult(list.size(), changed, sent, failed, skipped);
            log.info("Expiry check finished: {}", r);
            return r;
        } finally {
            lock.unlock();
        }
    }

    private boolean updateStatus(Certificate c, LocalDate today) {
        long daysLeft = ExpiryCalculator.daysLeft(c.getExpiryDate(), today);
        CertStatus s = ExpiryCalculator.status(daysLeft, ReminderDays.effective(c));
        if (s != c.getStatus()) {
            c.setStatus(s);
            certificates.save(c);
            return true;
        }
        return false;
    }
}
