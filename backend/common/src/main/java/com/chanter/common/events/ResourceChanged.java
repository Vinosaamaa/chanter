package com.chanter.common.events;

import java.util.UUID;

/** Source metadata only. Historical approval never grants current viewer access. */
public record ResourceChanged(UUID resourceId, UUID courseId, UUID studyServerId, String sourceSha256,
        String fileName, boolean aiApproved, boolean deleted) {
    public void validate() {
        if (resourceId == null || (!deleted && (courseId == null || studyServerId == null
                || sourceSha256 == null || !sourceSha256.matches("[a-f0-9]{64}")
                || fileName == null || fileName.isBlank() || fileName.length() > 180
                || fileName.chars().anyMatch(Character::isISOControl)))) {
            throw new IllegalArgumentException("Invalid resource change");
        }
    }
}
