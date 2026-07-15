package com.chanter.community.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum CommunityAnnouncementStatus {
    PUBLISHED,
    ARCHIVED;

    public static CommunityAnnouncementStatus fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return PUBLISHED;
        }
        try {
            return CommunityAnnouncementStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown announcement status");
        }
    }
}
