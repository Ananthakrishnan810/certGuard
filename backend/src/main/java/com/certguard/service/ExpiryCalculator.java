package com.certguard.service;

import com.certguard.entity.CertStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Pure expiry rules, kept free of Spring so they are easy to unit test.
 *
 * <p>Reminder keys: positive = N days before expiry, 0 = expiry day / first "expired" notice,
 * -7, -14, ... = weekly "still expired" reminders.
 */
public final class ExpiryCalculator {

    private ExpiryCalculator() {}

    public static long daysLeft(LocalDate expiry, LocalDate today) {
        return ChronoUnit.DAYS.between(today, expiry);
    }

    public static CertStatus status(long daysLeft, List<Integer> offsets) {
        if (daysLeft < 0) return CertStatus.EXPIRED;
        int window = offsets.stream().mapToInt(Integer::intValue).max().orElse(0);
        return daysLeft <= window ? CertStatus.EXPIRING_SOON : CertStatus.VALID;
    }

    /**
     * Which reminder (if any) is due today.
     *
     * <p>Before expiry we pick the smallest offset that is still >= daysLeft. That fires on the exact
     * day normally, but also catches up if the server was down on that day or the certificate was
     * uploaded inside the window - and because the caller de-duplicates by key, each offset is sent at
     * most once.
     */
    public static Optional<Integer> dueReminderKey(long daysLeft, List<Integer> offsets) {
        if (daysLeft > 0) {
            return offsets.stream().filter(o -> o > 0 && o >= daysLeft).min(Integer::compareTo);
        }
        if (daysLeft == 0) return Optional.of(0);
        long overdue = -daysLeft;
        long week = overdue / 7;
        return Optional.of(week == 0 ? 0 : (int) (-7 * week));
    }
}
