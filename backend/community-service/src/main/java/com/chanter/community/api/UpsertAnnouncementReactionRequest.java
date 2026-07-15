package com.chanter.community.api;

import jakarta.validation.constraints.NotNull;

public record UpsertAnnouncementReactionRequest(
        @NotNull Boolean liked
) {
}
