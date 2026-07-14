package com.chanter.community.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AssignTeachingAssistantRequest(
        @NotEmpty @Size(max = 100) List<@NotNull UUID> learnerUserIds,
        UUID teachingAssistantUserId
) {
}
