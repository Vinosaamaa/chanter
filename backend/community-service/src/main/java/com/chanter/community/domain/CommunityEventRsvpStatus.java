package com.chanter.community.domain;

public enum CommunityEventRsvpStatus {
    GOING,
    INTERESTED,
    NOT_GOING;

    public static CommunityEventRsvpStatus fromApiValue(String value) {
        return CommunityEventRsvpStatus.valueOf(value.trim().toUpperCase());
    }
}
