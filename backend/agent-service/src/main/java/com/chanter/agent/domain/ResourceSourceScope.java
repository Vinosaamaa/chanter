package com.chanter.agent.domain;

import java.util.UUID;

/** Source metadata does not grant access; retrieval still requires current viewer authorization. */
public record ResourceSourceScope(UUID studyServerId, UUID cohortId, String language,
        String accessScope, long sourceRevision) {
    public static ResourceSourceScope course(UUID server, long revision) {
        return new ResourceSourceScope(server, null, "und", server == null ? "UNKNOWN" : "COURSE", revision);
    }
}
