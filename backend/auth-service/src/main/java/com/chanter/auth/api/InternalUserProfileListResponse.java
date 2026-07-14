package com.chanter.auth.api;

import com.chanter.auth.application.AuthSessionService;
import java.util.List;

public record InternalUserProfileListResponse(List<InternalUserProfileResponse> profiles) {

    static InternalUserProfileListResponse from(List<AuthSessionService.AuthUserProfile> profiles) {
        return new InternalUserProfileListResponse(
                profiles.stream().map(InternalUserProfileResponse::from).toList()
        );
    }
}
