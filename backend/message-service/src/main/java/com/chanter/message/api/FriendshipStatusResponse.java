package com.chanter.message.api;

import com.chanter.message.domain.FriendshipSnapshot;
import java.util.UUID;

public record FriendshipStatusResponse(
        String status,
        UUID friendRequestId,
        UUID senderUserId,
        UUID recipientUserId
) {

    public static FriendshipStatusResponse from(FriendshipSnapshot snapshot) {
        return new FriendshipStatusResponse(
                snapshot.state().name(),
                snapshot.friendRequestId().orElse(null),
                snapshot.senderUserId().orElse(null),
                snapshot.recipientUserId().orElse(null)
        );
    }
}
