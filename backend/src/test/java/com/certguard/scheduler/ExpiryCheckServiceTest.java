package com.certguard.scheduler;

import com.certguard.dto.ExpiryCheckResult;
import com.certguard.entity.*;
import com.certguard.mail.MailService;
import com.certguard.repository.CertificateRepository;
import com.certguard.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpiryCheckServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);

    @Mock CertificateRepository certificates;
    @Mock NotificationLogRepository notifications;
    @Mock MailService mail;

    ExpiryCheckService service;
    User user;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(8, 0).atZone(IST).toInstant(), IST);
        service = new ExpiryCheckService(certificates, notifications, mail, clock);
        user = new User();
        user.setId(1L);
        user.setEmail("owner@example.com");
        user.setNotifyEmail("alerts@example.com");
        user.setDefaultReminderDays("30,15,7,1");
    }

    private Certificate cert(long id, LocalDate expiry, CertStatus status) {
        Certificate c = new Certificate();
        c.setId(id);
        c.setUser(user);
        c.setTitle("Cert " + id);
        c.setType(CertType.OTHER);
        c.setExpiryDate(expiry);
        c.setStatus(status);
        return c;
    }

    @Test
    void sendsDueReminderAndRecordsDedupKey() {
        Certificate c = cert(10, TODAY.plusDays(7), CertStatus.VALID);
        when(certificates.findAllActiveWithUser()).thenReturn(List.of(c));
        when(notifications.existsByDedupKey(anyString())).thenReturn(false);
        when(mail.sendExpiryReminder(c, 7, "alerts@example.com"))
                .thenReturn(new MailService.SendOutcome(true, 1, null));

        ExpiryCheckResult r = service.run(null);

        assertThat(r.sent()).isEqualTo(1);
        assertThat(r.statusChanged()).isEqualTo(1);
        assertThat(c.getStatus()).isEqualTo(CertStatus.EXPIRING_SOON);
        ArgumentCaptor<NotificationLog> log = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notifications).save(log.capture());
        assertThat(log.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(log.getValue().getOffsetDays()).isEqualTo(7);
        assertThat(log.getValue().getDedupKey()).isEqualTo("10:" + TODAY.plusDays(7) + ":7");
    }

    @Test
    void neverSendsTheSameReminderTwice() {
        Certificate c = cert(11, TODAY.plusDays(7), CertStatus.EXPIRING_SOON);
        when(certificates.findAllActiveWithUser()).thenReturn(List.of(c));
        when(notifications.existsByDedupKey("11:" + TODAY.plusDays(7) + ":7")).thenReturn(true);

        ExpiryCheckResult r = service.run(null);

        assertThat(r.sent()).isZero();
        verifyNoInteractions(mail);
        verify(notifications, never()).save(any());
    }

    @Test
    void failedSendIsLoggedWithoutDedupKeySoItRetries() {
        Certificate c = cert(12, TODAY, CertStatus.EXPIRING_SOON);
        when(certificates.findAllActiveWithUser()).thenReturn(List.of(c));
        when(mail.sendExpiryReminder(any(), eq(0L), anyString()))
                .thenReturn(new MailService.SendOutcome(false, 3, "SMTP down"));

        ExpiryCheckResult r = service.run(null);

        assertThat(r.failed()).isEqualTo(1);
        ArgumentCaptor<NotificationLog> log = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notifications).save(log.capture());
        assertThat(log.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(log.getValue().getAttempts()).isEqualTo(3);
        assertThat(log.getValue().getDedupKey()).isNull();
    }

    @Test
    void disabledRemindersOnlyUpdateStatus() {
        Certificate c = cert(13, TODAY.minusDays(1), CertStatus.EXPIRING_SOON);
        c.setRemindersEnabled(false);
        when(certificates.findAllActiveWithUser()).thenReturn(List.of(c));

        ExpiryCheckResult r = service.run(null);

        assertThat(c.getStatus()).isEqualTo(CertStatus.EXPIRED);
        assertThat(r.skippedDisabled()).isEqualTo(1);
        verifyNoInteractions(mail);
    }

    @Test
    void certificateOverridesUserDefaults() {
        Certificate c = cert(14, TODAY.plusDays(60), CertStatus.VALID);
        c.setReminderDays("90,60");
        when(certificates.findAllActiveWithUser()).thenReturn(List.of(c));
        when(mail.sendExpiryReminder(c, 60, "alerts@example.com"))
                .thenReturn(new MailService.SendOutcome(true, 1, null));

        service.run(null);

        verify(mail).sendExpiryReminder(c, 60, "alerts@example.com");
    }

    @Test
    void nothingDueFarFromExpiry() {
        Certificate c = cert(15, TODAY.plusDays(200), CertStatus.VALID);
        when(certificates.findAllActiveWithUser()).thenReturn(List.of(c));

        ExpiryCheckResult r = service.run(null);

        assertThat(r.sent()).isZero();
        verifyNoInteractions(mail);
        verify(certificates, never()).save(any());
    }
}
