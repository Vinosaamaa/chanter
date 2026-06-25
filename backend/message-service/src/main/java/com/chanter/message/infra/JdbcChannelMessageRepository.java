package com.chanter.message.infra;

import com.chanter.message.application.ChannelMessageRepository;
import com.chanter.message.domain.ChannelMessage;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    public List<ChannelMessage> listByChannelSince(UUID channelId, Optional<Instant> since, int limit) {
        String sql = since.isPresent()
                ? """
                SELECT id, channel_id, sender_user_id, body, created_at
                FROM channel_messages
                WHERE channel_id = :channelId
                AND created_at > :since
                ORDER BY created_at ASC
                LIMIT :limit
                """
                : """
                SELECT id, channel_id, sender_user_id, body, created_at
                FROM channel_messages
                WHERE channel_id = :channelId
                ORDER BY created_at ASC
                LIMIT :limit
                """;

        var query = jdbcClient.sql(sql)
                .param("channelId", channelId)
                .param("limit", limit);

        if (since.isPresent()) {
            query = query.param("since", Timestamp.from(since.get()));
        }

        return query.query((rs, rowNum) -> new ChannelMessage(
                rs.getObject("id", UUID.class),
                rs.getObject("channel_id", UUID.class),
                rs.getObject("sender_user_id", UUID.class),
                rs.getString("body"),
                rs.getTimestamp("created_at").toInstant()
        )).list();
    }
}
