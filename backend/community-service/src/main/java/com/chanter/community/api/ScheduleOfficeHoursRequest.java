package com.chanter.community.api;

import com.chanter.community.domain.CohortOfficeHoursAccess;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ScheduleOfficeHoursRequest(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull java.util.UUID instructorUserId
) {
}
