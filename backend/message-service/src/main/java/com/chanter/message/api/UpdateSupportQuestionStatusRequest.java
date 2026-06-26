package com.chanter.message.api;

import com.chanter.message.domain.SupportQuestionStatus;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateSupportQuestionStatusRequest(
        @NotNull SupportQuestionStatus status
) {
}
