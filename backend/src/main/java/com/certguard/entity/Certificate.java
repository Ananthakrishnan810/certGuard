package com.certguard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "certificates")
@Getter
@Setter
@NoArgsConstructor
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CertType type;

    @Column(length = 200)
    private String issuer;

    @Column(name = "cert_number", length = 120)
    private String certNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "notify_email", length = 190)
    private String notifyEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CertStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_source", nullable = false, columnDefinition = "enum('AUTO','MANUAL')")
    private DateSource dateSource = DateSource.MANUAL;

    /** Comma separated offsets in days; null means "use the user's defaults". */
    @Column(name = "reminder_days", length = 100)
    private String reminderDays;

    @Column(name = "reminders_enabled", nullable = false)
    private boolean remindersEnabled = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(length = 300)
    private String tags;

    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
