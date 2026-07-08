package com.chanter.message.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateDirectMessageRequest(
        @NotNull UUID recipientUserId,
        @NotBlank String body
) {
}
