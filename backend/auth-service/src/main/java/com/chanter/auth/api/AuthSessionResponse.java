package com.chanter.auth.api;

import com.chanter.auth.application.AuthSessionService;

public record AuthSessionResponse(
        String accessToken,
        long expiresInSeconds,
        AuthUserResponse user
) {

    static AuthSessionResponse from(AuthSessionService.AuthSession session) {
        return new AuthSessionResponse(
                session.accessToken(),
                session.expiresInSeconds(),
                AuthUserResponse.from(session.user())
        );
    }
}
