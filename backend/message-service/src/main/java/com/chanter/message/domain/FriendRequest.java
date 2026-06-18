package com.chanter.message.domain;

import java.time.Instant;
import java.util.UUID;

public record FriendRequest(
        UUID id,
        UUID senderUserId,
        UUID recipientUserId,
        FriendRequestStatus status,
        Instant createdAt
) {
}
