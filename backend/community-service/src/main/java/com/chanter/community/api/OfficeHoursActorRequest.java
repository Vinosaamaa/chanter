package com.chanter.community.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OfficeHoursActorRequest(
        // TODO(#auth): replace caller-supplied actor ids with the authenticated principal.
        @NotNull UUID actorUserId
) {
}
