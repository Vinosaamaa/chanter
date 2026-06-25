package com.chanter.community.api;

import com.chanter.community.domain.SaasPlanTier;
import jakarta.validation.constraints.NotNull;

public record UpdateSaasPlanRequest(
        @NotNull SaasPlanTier planTier
) {
}
