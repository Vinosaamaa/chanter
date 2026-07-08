package com.chanter.message.domain;

import java.time.Instant;
import java.util.UUID;

public record FriendSummary(
        UUID friendUserId,
        Instant friendsSince
) {
}
