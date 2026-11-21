package com.certguard.dto;

import java.util.List;

public record DashboardSummary(long total, long valid, long expiringSoon, long expired, long archived,
                               List<CertificateDto> upcoming) {}
