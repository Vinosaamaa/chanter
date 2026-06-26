package com.chanter.message.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupportQuestionRequest(
        @NotBlank String body,
        @NotBlank @Size(max = 128) String idempotencyKey
) {
}
