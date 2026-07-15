package com.chanter.community.domain;

public enum CommunityEventVisibility {
    HUB,
    COURSE,
    COHORT;

    public static CommunityEventVisibility fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return HUB;
        }
        return CommunityEventVisibility.valueOf(value.trim().toUpperCase());
    }
}
