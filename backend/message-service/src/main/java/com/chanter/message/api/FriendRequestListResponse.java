package com.chanter.message.api;

import com.chanter.message.domain.FriendRequest;
import java.util.List;

public record FriendRequestListResponse(
        List<FriendRequestResponse> incoming,
        List<FriendRequestResponse> outgoing
) {

    public static FriendRequestListResponse from(
            List<FriendRequest> incoming,
            List<FriendRequest> outgoing
    ) {
        return new FriendRequestListResponse(
                incoming.stream().map(FriendRequestResponse::from).toList(),
                outgoing.stream().map(FriendRequestResponse::from).toList()
        );
    }
}
