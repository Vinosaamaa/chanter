package com.chanter.community.api;

import com.chanter.community.domain.SaasPlanTier;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateSaasPlanRequest(
        // TODO(#auth): replace caller-supplied owner ids with the authenticated principal.
        @NotNull UUID ownerUserId,
        @NotNull SaasPlanTier planTier
) {
}
