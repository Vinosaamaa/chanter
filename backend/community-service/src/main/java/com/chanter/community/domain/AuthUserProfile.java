package com.chanter.community.domain;

import java.util.UUID;

public record AuthUserProfile(UUID userId, String email, String displayName) {
}
