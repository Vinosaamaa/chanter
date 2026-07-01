package com.chanter.message.application;

import com.chanter.message.domain.ChannelMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ChannelMessageRepository {

    ChannelMessage save(ChannelMessage message);

    List<ChannelMessage> listByChannelSince(
            UUID channelId,
            Optional<Instant> since,
            Optional<UUID> afterMessageId,
            int limit
    );

    long countByChannelCreatedBetween(
            UUID channelId,
            Instant windowStartInclusive,
            Instant windowEndExclusive
    );

    long countByChannelCreatedBetweenExcludingIds(
            UUID channelId,
            Instant windowStartInclusive,
            Instant windowEndExclusive,
            Set<UUID> excludedMessageIds
    );

    List<ChannelMessage> listByChannelCreatedBetween(
            UUID channelId,
            Instant windowStartInclusive,
            Instant windowEndExclusive,
            int limit
    );

    List<ChannelMessage> listByChannelCreatedBetweenExcludingIds(
            UUID channelId,
            Instant windowStartInclusive,
            Instant windowEndExclusive,
            Set<UUID> excludedMessageIds,
            int limit
    );
}
