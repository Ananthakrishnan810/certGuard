package com.certguard.dto;

import com.certguard.entity.CertType;
import com.certguard.entity.DateSource;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Create / update payload. reminderDays == null means "use my default offsets". */
public record CertificateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull CertType type,
        @Size(max = 200) String issuer,
        @Size(max = 120) String certNumber,
        LocalDate issueDate,
        @NotNull(message = "Expiry date is required") LocalDate expiryDate,
        @Email @Size(max = 190) String notifyEmail,
        DateSource dateSource,
        List<Integer> reminderDays,
        Boolean remindersEnabled,
        @Size(max = 5000) String notes,
        @Size(max = 300) String tags) {}
