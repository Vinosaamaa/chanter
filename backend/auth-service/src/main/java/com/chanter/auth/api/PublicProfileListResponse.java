package com.chanter.auth.api;

import com.chanter.auth.application.AuthSessionService;
import java.util.List;

public record PublicProfileListResponse(List<PublicProfileResponse> profiles) {

    public static PublicProfileListResponse from(
            List<AuthSessionService.AuthUserProfile> profiles
    ) {
        return new PublicProfileListResponse(
                profiles.stream().map(PublicProfileResponse::from).toList()
        );
    }
}
