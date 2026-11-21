package com.certguard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class SettingsDtos {
    private SettingsDtos() {}

    public record SettingsResponse(String name, String email, String notifyEmail, List<Integer> defaultReminderDays,
                                   String cron, String timezone, boolean mailConfigured, String mailSender) {}

    public record SettingsUpdate(
            @NotBlank @Size(max = 120) String name,
            @Email @Size(max = 190) String notifyEmail,
            @NotEmpty(message = "At least one reminder offset is required") List<Integer> defaultReminderDays) {}
}
