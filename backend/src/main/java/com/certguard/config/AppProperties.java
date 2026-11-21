package com.certguard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        String frontendUrl,
        List<String> corsOrigins,
        Jwt jwt,
        ExpiryCheck expiryCheck,
        Reminders reminders,
        Mail mail,
        Ocr ocr,
        boolean seedDemo) {

    public record Jwt(String secret, long expirationHours) {}
    public record ExpiryCheck(String cron) {}
    public record Reminders(String defaultDays) {}
    public record Mail(String fromName, int maxAttempts) {}
    public record Ocr(String tesseractCommand) {}
}
