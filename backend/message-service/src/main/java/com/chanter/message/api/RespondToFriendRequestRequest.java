package com.chanter.message.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RespondToFriendRequestRequest(
        // TODO(#auth): replace caller-supplied user ids with the authenticated principal.
        @NotNull UUID recipientUserId
) {
}
