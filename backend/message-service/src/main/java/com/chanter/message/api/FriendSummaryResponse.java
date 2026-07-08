package com.chanter.message.api;

import com.chanter.message.domain.FriendSummary;
import java.time.Instant;
import java.util.UUID;

public record FriendSummaryResponse(
        UUID friendUserId,
        Instant friendsSince
) {

    public static FriendSummaryResponse from(FriendSummary friendSummary) {
        return new FriendSummaryResponse(
                friendSummary.friendUserId(),
                friendSummary.friendsSince()
        );
    }
}
