package com.chanter.auth.api;

import com.chanter.auth.application.AuthSessionService;
import java.util.UUID;

public record AuthUserResponse(UUID id, String email, String displayName) {

    static AuthUserResponse from(AuthSessionService.AuthUserProfile user) {
        return new AuthUserResponse(user.id(), user.email(), user.displayName());
    }
}
