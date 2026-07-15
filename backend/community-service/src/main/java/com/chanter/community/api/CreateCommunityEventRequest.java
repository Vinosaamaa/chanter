package com.chanter.community.api;

import com.chanter.community.domain.CommunityEventVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateCommunityEventRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 4000) String description,
        @Size(max = 240) String location,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Positive Integer capacity,
        String visibility,
        UUID courseId,
        UUID cohortId
) {
    CommunityEventVisibility parsedVisibility() {
        return CommunityEventVisibility.fromApiValue(visibility);
    }
}
