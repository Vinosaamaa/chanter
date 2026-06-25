package com.chanter.message.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChannelMessageRequest(@NotBlank @Size(max = 4000) String body) {
}
