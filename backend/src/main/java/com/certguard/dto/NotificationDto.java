package com.certguard.dto;

import com.certguard.entity.NotificationLog;
import com.certguard.entity.NotificationStatus;

import java.time.Instant;
import java.time.LocalDate;

public record NotificationDto(Long id, int offsetDays, LocalDate expiryDate, String sentTo, Instant sentAt,
                              NotificationStatus status, int attempts, String error) {
    public static NotificationDto from(NotificationLog n) {
        return new NotificationDto(n.getId(), n.getOffsetDays(), n.getExpiryDate(), n.getSentTo(), n.getSentAt(),
                n.getStatus(), n.getAttempts(), n.getError());
    }
}
