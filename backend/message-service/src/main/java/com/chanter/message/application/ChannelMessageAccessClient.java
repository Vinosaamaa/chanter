package com.chanter.message.application;

import com.chanter.message.domain.ChannelScope;
import java.util.UUID;

public interface ChannelMessageAccessClient {

    ChannelMessageAccess requireAccess(UUID channelId, UUID userId, ChannelScope channelScope);
}
