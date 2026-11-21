package com.certguard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certificate_id", nullable = false)
    private Certificate certificate;

    /** Days before expiry (positive), 0 = expiry day, negative = weeks overdue (-7, -14, ...). */
    @Column(name = "offset_days", nullable = false)
    private int offsetDays;

    /** Expiry date the reminder refers to, so renewing a certificate re-arms its reminders. */
    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "sent_to", nullable = false, length = 190)
    private String sentTo;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 1000)
    private String error;

    @Column(name = "dedup_key", length = 100, unique = true)
    private String dedupKey;

    public static String dedupKey(long certificateId, LocalDate expiryDate, int offsetDays) {
        return certificateId + ":" + expiryDate + ":" + offsetDays;
    }
}
