package com.chanter.auth.api;

import com.chanter.auth.application.AuthSessionService;
import java.util.UUID;

public record PublicProfileResponse(UUID userId, String displayName) {

    public static PublicProfileResponse from(AuthSessionService.AuthUserProfile profile) {
        return new PublicProfileResponse(profile.id(), profile.displayName());
    }
}
