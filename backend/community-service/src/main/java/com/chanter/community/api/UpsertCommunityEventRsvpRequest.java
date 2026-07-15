package com.chanter.community.api;

import com.chanter.community.domain.CommunityEventRsvpStatus;
import jakarta.validation.constraints.NotBlank;

public record UpsertCommunityEventRsvpRequest(
        @NotBlank String status
) {
    CommunityEventRsvpStatus parsedStatus() {
        return CommunityEventRsvpStatus.fromApiValue(status);
    }
}
