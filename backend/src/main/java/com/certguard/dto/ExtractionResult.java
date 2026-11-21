package com.certguard.dto;

import com.certguard.entity.CertType;

import java.time.LocalDate;

/**
 * Values read from the uploaded file. Nothing is saved; the UI shows these for confirmation.
 *
 * @param fileKind         PDF, IMAGE, X509, PKCS12
 * @param method           how dates were found: X509, PDF_TEXT, OCR, NONE
 * @param requiresPassword true when a .p12/.pfx needs its keystore password
 */
public record ExtractionResult(
        String fileKind,
        String method,
        boolean datesFound,
        boolean requiresPassword,
        String title,
        CertType type,
        String issuer,
        String certNumber,
        LocalDate issueDate,
        LocalDate expiryDate,
        String message,
        String textSnippet) {}
