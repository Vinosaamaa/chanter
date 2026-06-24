package com.chanter.message.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record UpsertApprovedFaqRequest(
        // TODO(#auth): replace caller-supplied user ids with the authenticated principal.
        @NotNull UUID channelId,
        @NotNull UUID approvedByUserId,
        UUID id,
        @NotBlank String question,
        @NotBlank String answer,
        @NotEmpty @Size(max = 64) List<@NotNull UUID> sourceSupportQuestionIds
) {
}
