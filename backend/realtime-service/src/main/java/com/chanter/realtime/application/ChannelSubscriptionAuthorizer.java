package com.chanter.realtime.application;

import com.chanter.realtime.domain.RealtimeChannelScope;
import java.util.UUID;

public interface ChannelSubscriptionAuthorizer {

    void requireSubscribeAccess(UUID channelId, UUID userId, RealtimeChannelScope channelScope);
}
