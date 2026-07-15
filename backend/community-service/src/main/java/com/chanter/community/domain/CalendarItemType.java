package com.chanter.community.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum CalendarItemType {
    OFFICE_HOURS,
    EVENT,
    DEADLINE;

    public static Set<CalendarItemType> parseTypes(String raw) {
        if (raw == null || raw.isBlank()) {
            return EnumSet.allOf(CalendarItemType.class);
        }
        EnumSet<CalendarItemType> types = EnumSet.noneOf(CalendarItemType.class);
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .forEach(part -> {
                    String normalized = part.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
                    if ("GOING".equals(normalized)) {
                        // GOING is an RSVP filter handled separately; ignore as item type.
                        return;
                    }
                    types.add(CalendarItemType.valueOf(normalized));
                });
        return types.isEmpty() ? EnumSet.allOf(CalendarItemType.class) : types;
    }

    public static boolean includesGoingFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .anyMatch(part -> "GOING".equalsIgnoreCase(part));
    }
}
