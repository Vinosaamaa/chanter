package com.chanter.message.api;

import jakarta.validation.constraints.NotBlank;

public record CreateChannelMessageRequest(@NotBlank String body) {
}
