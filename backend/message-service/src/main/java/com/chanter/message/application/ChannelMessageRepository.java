package com.chanter.message.application;

import com.chanter.message.domain.ChannelMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelMessageRepository {

    ChannelMessage save(ChannelMessage message);

    List<ChannelMessage> listByChannelSince(UUID channelId, Optional<Instant> since, int limit);
}
