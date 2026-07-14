package com.chanter.community.api;

import jakarta.validation.constraints.NotNull;

public record UpdateOfficeHoursSpeakingRequest(@NotNull Boolean canSpeak) {
}
