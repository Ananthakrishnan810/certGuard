package com.certguard.dto;

import com.certguard.entity.User;
import com.certguard.service.ReminderDays;

import java.util.List;

public record UserDto(Long id, String name, String email, String role, String notifyEmail,
                      List<Integer> defaultReminderDays) {
    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.effectiveNotifyEmail(),
                ReminderDays.parse(u.getDefaultReminderDays()));
    }
}
