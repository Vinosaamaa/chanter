package com.chanter.message.api;

import com.chanter.message.domain.FriendRequest;
import java.time.Instant;
import java.util.UUID;

public record FriendRequestResponse(
        UUID id,
        UUID senderUserId,
        UUID recipientUserId,
        String status,
        Instant createdAt
) {

    public static FriendRequestResponse from(FriendRequest friendRequest) {
        return new FriendRequestResponse(
                friendRequest.id(),
                friendRequest.senderUserId(),
                friendRequest.recipientUserId(),
                friendRequest.status().name(),
                friendRequest.createdAt()
        );
    }
}
