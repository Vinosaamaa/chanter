package com.chanter.message.api;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record InstructorDashboardMessageMetricsRequest(
        @NotNull UUID viewerUserId,
        @NotNull List<UUID> questionChannelIds,
        @NotNull List<UUID> cohortIds,
        @NotNull List<UUID> courseIds
) {
}
