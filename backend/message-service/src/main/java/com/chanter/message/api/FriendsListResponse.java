package com.chanter.message.api;

import com.chanter.message.domain.FriendSummary;
import java.util.List;

public record FriendsListResponse(List<FriendSummaryResponse> friends) {

    public static FriendsListResponse from(List<FriendSummary> friends) {
        return new FriendsListResponse(friends.stream()
                .map(FriendSummaryResponse::from)
                .toList());
    }
}
