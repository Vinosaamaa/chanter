package com.chanter.message.domain;

import java.util.Optional;
import java.util.UUID;

public record FriendshipSnapshot(
        FriendshipState state,
        Optional<UUID> friendRequestId,
        Optional<UUID> senderUserId,
        Optional<UUID> recipientUserId
) {

    public static FriendshipSnapshot none() {
        return new FriendshipSnapshot(FriendshipState.NONE, Optional.empty(), Optional.empty(), Optional.empty());
    }
}
