package com.chanter.message.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TaQueueActorRequest(
        @NotNull UUID actorUserId
) {
}
