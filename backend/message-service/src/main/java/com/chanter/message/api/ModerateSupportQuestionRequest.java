package com.chanter.message.api;

import com.chanter.message.domain.SupportQuestionStatus;
import jakarta.validation.constraints.NotNull;

public record ModerateSupportQuestionRequest(
        @NotNull SupportQuestionStatus status
) {
}
