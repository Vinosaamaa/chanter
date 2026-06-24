package com.chanter.agent.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InvokeAssistantAnswerRequest(@NotNull UUID learnerUserId) {
}
