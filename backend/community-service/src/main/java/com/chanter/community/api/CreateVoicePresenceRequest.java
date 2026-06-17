package com.chanter.community.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateVoicePresenceRequest(
        @NotNull UUID memberUserId
) {
}
