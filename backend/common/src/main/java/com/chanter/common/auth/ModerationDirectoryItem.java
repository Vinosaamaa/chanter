package com.chanter.common.auth;

import java.util.UUID;

/** Coarse operator lookup metadata; evidence requires a separate case-authorized read. */
public record ModerationDirectoryItem(String type, UUID id, String name) {
    public ModerationDirectoryItem {
        if (!java.util.Set.of("USER","STUDY_SERVER").contains(type) || id == null || name == null || name.length() > 255)
            throw new IllegalArgumentException("Invalid moderation directory item");
    }
}
