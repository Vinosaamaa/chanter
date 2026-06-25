package com.chanter.auth.api;

import com.chanter.auth.application.AuthSessionService;
import java.util.UUID;

public record AuthSessionResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        AuthUserResponse user
) {

    static AuthSessionResponse from(AuthSessionService.AuthSession session) {
        return new AuthSessionResponse(
                session.accessToken(),
                session.refreshToken(),
                session.expiresInSeconds(),
                AuthUserResponse.from(session.user())
        );
    }
}
