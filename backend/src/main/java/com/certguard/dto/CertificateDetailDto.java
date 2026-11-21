package com.certguard.dto;

import java.util.List;

public record CertificateDetailDto(CertificateDto certificate, List<FileMeta> files,
                                   List<NotificationDto> notifications) {}
