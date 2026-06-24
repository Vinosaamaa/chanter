package com.chanter.community.api;

import com.chanter.community.domain.SaasPlanTier;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateSaasPlanRequest(
        @NotNull UUID ownerUserId,
        @NotNull SaasPlanTier planTier
) {
}
