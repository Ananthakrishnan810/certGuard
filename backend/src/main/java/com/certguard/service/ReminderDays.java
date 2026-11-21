package com.certguard.service;

import com.certguard.entity.Certificate;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Reminder offsets are stored as "30,15,7,1": days before expiry. 0 = on the expiry day. */
public final class ReminderDays {
    public static final int MAX_OFFSET = 3650;

    private ReminderDays() {}

    public static List<Integer> parse(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return normalize(Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList());
    }

    /** Distinct, within 0..3650, largest first. */
    public static List<Integer> normalize(Collection<Integer> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(Objects::nonNull)
                .filter(v -> v >= 0 && v <= MAX_OFFSET)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    public static String format(Collection<Integer> values) {
        return normalize(values).stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** The certificate's own offsets, or the owner's defaults when it has none. Requires user loaded. */
    public static List<Integer> effective(Certificate c) {
        List<Integer> own = parse(c.getReminderDays());
        return own.isEmpty() ? parse(c.getUser().getDefaultReminderDays()) : own;
    }
}
