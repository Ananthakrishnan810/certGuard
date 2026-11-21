package com.certguard.extraction;

import com.certguard.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * OCR through the tesseract command line tool. If tesseract is not installed, OCR is simply
 * unavailable and the user types the dates in manually.
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final String command;
    private volatile Boolean available;

    public OcrService(AppProperties props) {
        this.command = props.ocr().tesseractCommand();
    }

    public boolean isAvailable() {
        if (available == null) {
            try {
                Process p = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
                available = p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
            } catch (Exception e) {
                available = false;
            }
            log.info("Tesseract OCR available: {}", available);
        }
        return available;
    }

    public Optional<String> ocr(byte[] imageBytes, String suffix) {
        if (!isAvailable()) return Optional.empty();
        Path tmp = null;
        try {
            tmp = Files.createTempFile("certguard-ocr-", suffix);
            Files.write(tmp, imageBytes);
            return run(tmp);
        } catch (IOException e) {
            log.warn("OCR failed: {}", e.getMessage());
            return Optional.empty();
        } finally {
            deleteQuietly(tmp);
        }
    }

    public Optional<String> ocr(List<BufferedImage> pages) {
        if (!isAvailable() || pages.isEmpty()) return Optional.empty();
        StringBuilder all = new StringBuilder();
        for (BufferedImage img : pages) {
            Path tmp = null;
            try {
                tmp = Files.createTempFile("certguard-ocr-", ".png");
                ImageIO.write(img, "png", tmp.toFile());
                run(tmp).ifPresent(t -> all.append(t).append('\n'));
            } catch (IOException e) {
                log.warn("OCR page failed: {}", e.getMessage());
            } finally {
                deleteQuietly(tmp);
            }
        }
        return all.isEmpty() ? Optional.empty() : Optional.of(all.toString());
    }

    private Optional<String> run(Path image) {
        try {
            Process p = new ProcessBuilder(command, image.toString(), "stdout", "-l", "eng")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            byte[] out = p.getInputStream().readAllBytes();
            if (!p.waitFor(90, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return Optional.empty();
            }
            String text = new String(out, StandardCharsets.UTF_8);
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (IOException e) {
            log.warn("tesseract failed: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static void deleteQuietly(Path p) {
        if (p == null) return;
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }
}
