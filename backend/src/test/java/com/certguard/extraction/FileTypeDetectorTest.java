package com.certguard.extraction;

import com.certguard.service.ApiException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileTypeDetectorTest {

    @Test
    void detectsByMagicBytes() {
        assertThat(FileTypeDetector.validate("a.pdf", "%PDF-1.7 ...".getBytes())).isEqualTo(FileKind.PDF);
        assertThat(FileTypeDetector.validate("a.PNG", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2})).isEqualTo(FileKind.PNG);
        assertThat(FileTypeDetector.validate("a.crt", "-----BEGIN CERTIFICATE-----".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(FileKind.PEM);
        assertThat(FileTypeDetector.validate("a.p12", new byte[]{0x30, (byte) 0x82, 1, 2})).isEqualTo(FileKind.ASN1);
    }

    @Test
    void rejectsRenamedFile() {
        assertThatThrownBy(() -> FileTypeDetector.validate("evil.pdf", "MZ\u0090\u0000 exe".getBytes()))
                .isInstanceOf(ApiException.class).hasMessageContaining("does not match");
    }

    @Test
    void rejectsUnsupportedExtension() {
        assertThatThrownBy(() -> FileTypeDetector.validate("x.exe", "%PDF".getBytes()))
                .isInstanceOf(ApiException.class).hasMessageContaining("Unsupported");
    }
}
