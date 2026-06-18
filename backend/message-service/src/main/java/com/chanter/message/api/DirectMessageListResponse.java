package com.chanter.message.api;

import com.chanter.message.domain.DirectMessage;
import java.util.List;

public record DirectMessageListResponse(List<DirectMessageResponse> messages) {

    public static DirectMessageListResponse from(List<DirectMessage> directMessages) {
        return new DirectMessageListResponse(directMessages.stream()
                .map(DirectMessageResponse::from)
                .toList());
    }
}
