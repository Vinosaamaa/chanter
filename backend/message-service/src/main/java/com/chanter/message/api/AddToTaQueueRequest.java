package com.chanter.message.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddToTaQueueRequest(
        @NotNull UUID channelId,
        @NotNull UUID supportQuestionId,
        @NotNull UUID actorUserId
) {
}
