package com.chanter.community.domain;

public enum CommunityEventFilter {
    UPCOMING,
    PAST,
    GOING;

    public static CommunityEventFilter fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return UPCOMING;
        }
        return CommunityEventFilter.valueOf(value.trim().toUpperCase());
    }
}
