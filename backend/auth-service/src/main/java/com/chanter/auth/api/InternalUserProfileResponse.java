package com.chanter.auth.api;

import com.chanter.auth.application.AuthSessionService;
import java.util.UUID;

public record InternalUserProfileResponse(UUID userId, String email, String displayName) {

    static InternalUserProfileResponse from(AuthSessionService.AuthUserProfile profile) {
        return new InternalUserProfileResponse(profile.id(), profile.email(), profile.displayName());
    }
}
