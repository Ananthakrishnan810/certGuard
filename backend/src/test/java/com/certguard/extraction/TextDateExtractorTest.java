package com.certguard.extraction;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TextDateExtractorTest {

    @Test
    void labelledDayMonthYearNumeric() {
        var r = TextDateExtractor.extract("""
                CERTIFICATE OF COMPLIANCE
                Certificate No: ABC-2025/117
                Date of Issue: 05/03/2024
                Expiry Date: 04-03-2027
                """);
        assertThat(r.issueDate()).isEqualTo(LocalDate.of(2024, 3, 5));
        assertThat(r.expiryDate()).isEqualTo(LocalDate.of(2027, 3, 4));
        assertThat(r.certNumber()).isEqualTo("ABC-2025/117");
        assertThat(r.labelled()).isTrue();
    }

    @Test
    void monthNamesAndOrdinals() {
        var r = TextDateExtractor.extract("Issued on 1st January 2025. This licence is valid until March 31, 2026.");
        assertThat(r.issueDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(r.expiryDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void validFromToRange() {
        var r = TextDateExtractor.extract("Policy period: Valid from 15-Jan-2025 to 14-Jan-2026");
        assertThat(r.issueDate()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(r.expiryDate()).isEqualTo(LocalDate.of(2026, 1, 14));
    }

    @Test
    void isoDatesAndExpiresKeyword() {
        var r = TextDateExtractor.extract("Effective date: 2024-07-01\nExpires: 2025-06-30");
        assertThat(r.issueDate()).isEqualTo(LocalDate.of(2024, 7, 1));
        assertThat(r.expiryDate()).isEqualTo(LocalDate.of(2025, 6, 30));
    }

    @Test
    void usStyleDateWhenDayIsOver12() {
        var r = TextDateExtractor.extract("Expiration: 12/25/2026");
        assertThat(r.expiryDate()).isEqualTo(LocalDate.of(2026, 12, 25));
    }

    @Test
    void unlabelledFallsBackToEarliestAndLatest() {
        var r = TextDateExtractor.extract("Something 10 June 2023 then 9 June 2026 and 1 Jan 2024");
        assertThat(r.labelled()).isFalse();
        assertThat(r.issueDate()).isEqualTo(LocalDate.of(2023, 6, 10));
        assertThat(r.expiryDate()).isEqualTo(LocalDate.of(2026, 6, 9));
    }

    @Test
    void invalidDatesIgnored() {
        var r = TextDateExtractor.extract("Valid until 31/02/2026");
        assertThat(r.expiryDate()).isNull();
    }

    @Test
    void certificateNotesIsNotACertificateNumber() {
        var r = TextDateExtractor.extract("Certificate Notes: none\nValid until 01/01/2030");
        assertThat(r.certNumber()).isNull();
    }

    @Test
    void emptyText() {
        var r = TextDateExtractor.extract("   ");
        assertThat(r.expiryDate()).isNull();
        assertThat(r.issueDate()).isNull();
    }
}
