package com.certguard.dto;

import java.time.Instant;

public record FileMeta(Long id, Long certificateId, String fileName, String mimeType, long sizeBytes,
                       String sha256, int version, Instant uploadedAt) {}
