package com.certguard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "certificate_files")
@Getter
@Setter
@NoArgsConstructor
public class CertificateFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certificate_id", nullable = false)
    private Certificate certificate;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String sha256;

    /** The digital copy itself. Only loaded when a file is downloaded (list queries use projections). */
    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;

    @Column(nullable = false)
    private int version;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) uploadedAt = Instant.now();
    }
}
