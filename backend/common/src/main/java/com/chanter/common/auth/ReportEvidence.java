package com.chanter.common.auth;

import java.util.UUID;

/** Bounded report snapshot. Contains no object keys, bytes, passwords or bearer credentials. */
public record ReportEvidence(String type, UUID id, UUID authorId, UUID studyServerId, UUID courseId,
        UUID channelId, String title, String excerpt, String sourceFingerprint) {
    public ReportEvidence {
        if (id == null || type == null || !java.util.Set.of("USER","DM","MESSAGE","RESOURCE","STUDY_SERVER").contains(type))
            throw new IllegalArgumentException("Invalid evidence identity");
        if (title == null || title.length() > 255 || excerpt == null || excerpt.length() > 8000
                || (sourceFingerprint != null && sourceFingerprint.length() > 128))
            throw new IllegalArgumentException("Evidence exceeds its bounded snapshot");
    }
}
