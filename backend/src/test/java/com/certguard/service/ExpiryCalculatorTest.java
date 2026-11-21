package com.certguard.service;

import com.certguard.entity.CertStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiryCalculatorTest {

    private static final List<Integer> OFFSETS = List.of(30, 15, 7, 1);

    @Test
    void daysLeft() {
        LocalDate today = LocalDate.of(2026, 11, 21);
        assertThat(ExpiryCalculator.daysLeft(LocalDate.of(2026, 11, 30), today)).isEqualTo(9);
        assertThat(ExpiryCalculator.daysLeft(today, today)).isZero();
        assertThat(ExpiryCalculator.daysLeft(LocalDate.of(2026, 11, 20), today)).isEqualTo(-1);
    }

    @Test
    void status() {
        assertThat(ExpiryCalculator.status(31, OFFSETS)).isEqualTo(CertStatus.VALID);
        assertThat(ExpiryCalculator.status(30, OFFSETS)).isEqualTo(CertStatus.EXPIRING_SOON);
        assertThat(ExpiryCalculator.status(0, OFFSETS)).isEqualTo(CertStatus.EXPIRING_SOON);
        assertThat(ExpiryCalculator.status(-1, OFFSETS)).isEqualTo(CertStatus.EXPIRED);
    }

    @Test
    void reminderFiresOnExactOffsetDay() {
        assertThat(ExpiryCalculator.dueReminderKey(30, OFFSETS)).contains(30);
        assertThat(ExpiryCalculator.dueReminderKey(15, OFFSETS)).contains(15);
        assertThat(ExpiryCalculator.dueReminderKey(1, OFFSETS)).contains(1);
    }

    @Test
    void reminderCatchesUpBetweenOffsets() {
        // e.g. the server was down on day 15 or the certificate was uploaded with 12 days left
        assertThat(ExpiryCalculator.dueReminderKey(12, OFFSETS)).contains(15);
        assertThat(ExpiryCalculator.dueReminderKey(3, OFFSETS)).contains(7);
    }

    @Test
    void nothingDueOutsideWindow() {
        assertThat(ExpiryCalculator.dueReminderKey(45, OFFSETS)).isEqualTo(Optional.empty());
    }

    @Test
    void expiryDayAndWeeklyOverdue() {
        assertThat(ExpiryCalculator.dueReminderKey(0, OFFSETS)).contains(0);
        assertThat(ExpiryCalculator.dueReminderKey(-3, OFFSETS)).contains(0);   // first "expired" mail
        assertThat(ExpiryCalculator.dueReminderKey(-7, OFFSETS)).contains(-7);
        assertThat(ExpiryCalculator.dueReminderKey(-13, OFFSETS)).contains(-7);
        assertThat(ExpiryCalculator.dueReminderKey(-14, OFFSETS)).contains(-14);
    }

    @Test
    void reminderDaysParsing() {
        assertThat(ReminderDays.parse("7, 30,x,1,30,-2,9999")).containsExactly(30, 7, 1);
        assertThat(ReminderDays.format(List.of(1, 30, 7))).isEqualTo("30,7,1");
    }
}
