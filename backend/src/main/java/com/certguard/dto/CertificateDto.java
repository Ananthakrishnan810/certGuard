package com.certguard.dto;

import com.certguard.entity.CertStatus;
import com.certguard.entity.CertType;
import com.certguard.entity.DateSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CertificateDto(
        Long id,
        String title,
        CertType type,
        String issuer,
        String certNumber,
        LocalDate issueDate,
        LocalDate expiryDate,
        String notifyEmail,
        long daysLeft,
        CertStatus status,
        DateSource dateSource,
        List<Integer> reminderDays,
        boolean usesDefaultReminders,
        boolean remindersEnabled,
        String notes,
        String tags,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        FileMeta latestFile) {}
