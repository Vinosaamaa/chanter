package com.chanter.community.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UpdateOfficeHoursRequest(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {
}
