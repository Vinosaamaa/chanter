package com.chanter.message.infra;

import com.chanter.message.application.ChannelMessageRepository;
import com.chanter.message.domain.ChannelMessage;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcChannelMessageRepository implements ChannelMessageRepository {

    private final JdbcClient jdbcClient;

    public JdbcChannelMessageRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public ChannelMessage save(ChannelMessage message) {
        jdbcClient.sql("""
                        INSERT INTO channel_messages (id, channel_id, sender_user_id, body, created_at)
                        VALUES (:id, :channelId, :senderUserId, :body, :createdAt)
                        """)
                .param("id", message.id())
                .param("channelId", message.channelId())
                .param("senderUserId", message.senderUserId())
                .param("body", message.body())
                .param("createdAt", Timestamp.from(message.createdAt()))
                .update();

        return message;
    }

    @Override
    public List<ChannelMessage> listByChannelSince(
            UUID channelId,
            Optional<Instant> since,
            Optional<UUID> afterMessageId,
            int limit
    ) {
        String sql = since.isPresent()
                ? """
                SELECT id, channel_id, sender_user_id, body, created_at
                FROM channel_messages
                WHERE channel_id = :channelId
                AND (
                    created_at > :since
                    OR (:afterMessageId IS NOT NULL AND created_at = :since AND id > :afterMessageId)
                )
                ORDER BY created_at ASC, id ASC
                LIMIT :limit
                """
                : """
                SELECT id, channel_id, sender_user_id, body, created_at
                FROM channel_messages
                WHERE channel_id = :channelId
                ORDER BY created_at ASC, id ASC
                LIMIT :limit
                """;

        var query = jdbcClient.sql(sql)
                .param("channelId", channelId)
                .param("limit", limit);

        if (since.isPresent()) {
            query = query
                    .param("since", Timestamp.from(since.get()))
                    .param("afterMessageId", afterMessageId.orElse(null));
        }

        return query.query((rs, rowNum) -> new ChannelMessage(
                rs.getObject("id", UUID.class),
                rs.getObject("channel_id", UUID.class),
                rs.getObject("sender_user_id", UUID.class),
                rs.getString("body"),
                rs.getTimestamp("created_at").toInstant()
        )).list();
    }

    @Override
    public long countByChannelCreatedBetween(
            UUID channelId,
            Instant windowStartInclusive,
            Instant windowEndExclusive
    ) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM channel_messages
                        WHERE channel_id = :channelId
                        AND created_at >= :windowStartInclusive
                        AND created_at < :windowEndExclusive
                        """)
                .param("channelId", channelId)
                .param("windowStartInclusive", Timestamp.from(windowStartInclusive))
                .param("windowEndExclusive", Timestamp.from(windowEndExclusive))
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    @Override
    public long countByChannelCreatedBetweenExcludingIds(
            UUID channelId,
            Instant windowStartInclusive,
            Instant windowEndExclusive,
            Set<UUID> excludedMessageIds
    ) {
        if (excludedMessageIds.isEmpty()) {
            return countByChannelCreatedBetween(channelId, windowStartInclusive, windowEndExclusive);
        }

        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM channel_messages
                        WHERE channel_id = :channelId
                        AND created_at >= :windowStartInclusive
                        AND created_at < :windowEndExclusive
                        AND id NOT IN (:excludedMessageIds)
                        """)
                .param("channelId", channelId)
                .param("windowStartInclusive", Timestamp.from(windowStartInclusive))
                .param("windowEndExclusive", Timestamp.from(windowEndExclusive))
                .param("excludedMessageIds", excludedMessageIds)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    @Override
    public List<ChannelMessage> listByChannelCreatedBetween(
            UUID channelId,
            Instant windowStartInclusive,
            Instant windowEndExclusive,
            int limit
    ) {
        return jdbcClient.sql("""
                        SELECT id, channel_id, sender_user_id, body, created_at
                        FROM channel_messages
                        WHERE channel_id = :channelId
                        AND created_at >= :windowStartInclusive
                        AND created_at < :windowEndExclusive
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit
                        """)
                .param("channelId", channelId)
                .param("windowStartInclusive", Timestamp.from(windowStartInclusive))
                .param("windowEndExclusive", Timestamp.from(windowEndExclusive))
                .param("limit", limit)
                .query((rs, rowNum) -> new ChannelMessage(
                        rs.getObject("id", UUID.class),
                        rs.getObject("channel_id", UUID.class),
                        rs.getObject("sender_user_id", UUID.class),
                        rs.getString("body"),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .list();
    }

    @Override
    public List<ChannelMessage> listByChannelCreatedBetweenExcludingIds(
            UUID channelId,
            Instant windowStartInclusive,
            Instant windowEndExclusive,
            Set<UUID> excludedMessageIds,
            int limit
    ) {
        if (excludedMessageIds.isEmpty()) {
            return listByChannelCreatedBetween(channelId, windowStartInclusive, windowEndExclusive, limit);
        }

        return jdbcClient.sql("""
                        SELECT id, channel_id, sender_user_id, body, created_at
                        FROM channel_messages
                        WHERE channel_id = :channelId
                        AND created_at >= :windowStartInclusive
                        AND created_at < :windowEndExclusive
                        AND id NOT IN (:excludedMessageIds)
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit
                        """)
                .param("channelId", channelId)
                .param("windowStartInclusive", Timestamp.from(windowStartInclusive))
                .param("windowEndExclusive", Timestamp.from(windowEndExclusive))
                .param("excludedMessageIds", excludedMessageIds)
                .param("limit", limit)
                .query((rs, rowNum) -> new ChannelMessage(
                        rs.getObject("id", UUID.class),
                        rs.getObject("channel_id", UUID.class),
                        rs.getObject("sender_user_id", UUID.class),
                        rs.getString("body"),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .list();
    }
}
