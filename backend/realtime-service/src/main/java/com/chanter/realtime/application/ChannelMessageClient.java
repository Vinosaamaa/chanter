package com.chanter.realtime.application;

import com.chanter.realtime.domain.RealtimeChannelScope;
import java.util.UUID;

public interface ChannelMessageClient {

    PersistedChannelMessage postMessage(
            UUID channelId,
            UUID senderUserId,
            RealtimeChannelScope channelScope,
            String body
    );
}
