package com.chanter.community.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateVoicePresenceRequest(
        // TODO(#auth): replace caller-supplied member ids with the authenticated principal.
        @NotNull UUID memberUserId
) {
}
