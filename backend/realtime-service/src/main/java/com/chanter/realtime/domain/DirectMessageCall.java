package com.chanter.realtime.domain;

import java.time.Instant;
import java.util.UUID;

public record DirectMessageCall(
        UUID callId,
        UUID callerUserId,
        UUID calleeUserId,
        DirectMessageCallStatus status,
        Instant createdAt
) {
}
