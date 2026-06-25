package com.chanter.message.api;

import java.util.List;

public record ChannelMessageListResponse(List<ChannelMessageResponse> messages) {
}
