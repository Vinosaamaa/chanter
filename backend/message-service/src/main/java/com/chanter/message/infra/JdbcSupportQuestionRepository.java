package com.chanter.message.infra;

import com.chanter.message.application.SupportQuestionRepository;
import com.chanter.message.domain.SupportQuestion;
import com.chanter.message.domain.SupportQuestionStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcSupportQuestionRepository implements SupportQuestionRepository {

    private final JdbcClient jdbcClient;

    public JdbcSupportQuestionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public SupportQuestion saveSupportQuestion(SupportQuestion supportQuestion) {
        OffsetDateTime createdAt = OffsetDateTime.ofInstant(supportQuestion.createdAt(), ZoneOffset.UTC);

        jdbcClient.sql("""
                        INSERT INTO channel_messages (id, channel_id, sender_user_id, body, created_at)
                        VALUES (:messageId, :channelId, :senderUserId, :body, :createdAt)
                        """)
                .param("messageId", supportQuestion.channelMessageId())
                .param("channelId", supportQuestion.channelId())
                .param("senderUserId", supportQuestion.senderUserId())
                .param("body", supportQuestion.body())
                .param("createdAt", createdAt)
                .update();

        jdbcClient.sql("""
                        INSERT INTO support_questions (
                            id,
                            channel_message_id,
                            channel_id,
                            sender_user_id,
                            body,
                            status,
                            idempotency_key,
                            created_at
                        )
                        VALUES (
                            :id,
                            :channelMessageId,
                            :channelId,
                            :senderUserId,
                            :body,
                            :status,
                            :idempotencyKey,
                            :createdAt
                        )
                        """)
                .param("id", supportQuestion.id())
                .param("channelMessageId", supportQuestion.channelMessageId())
                .param("channelId", supportQuestion.channelId())
                .param("senderUserId", supportQuestion.senderUserId())
                .param("body", supportQuestion.body())
                .param("status", supportQuestion.status().name())
                .param("idempotencyKey", supportQuestion.idempotencyKey())
                .param("createdAt", createdAt)
                .update();

        return findByChannelSenderAndIdempotencyKey(
                supportQuestion.channelId(),
                supportQuestion.senderUserId(),
                supportQuestion.idempotencyKey()
        ).orElseThrow(() -> new IllegalStateException("Support Question was not persisted"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupportQuestion> findByChannelSenderAndIdempotencyKey(
            UUID channelId,
            UUID senderUserId,
            String idempotencyKey
    ) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            channel_message_id,
                            channel_id,
                            sender_user_id,
                            body,
                            status,
                            idempotency_key,
                            created_at
                        FROM support_questions
                        WHERE channel_id = :channelId
                        AND sender_user_id = :senderUserId
                        AND idempotency_key = :idempotencyKey
                        """)
                .param("channelId", channelId)
                .param("senderUserId", senderUserId)
                .param("idempotencyKey", idempotencyKey)
                .query(this::mapSupportQuestion)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportQuestion> findByChannelId(UUID channelId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            channel_message_id,
                            channel_id,
                            sender_user_id,
                            body,
                            status,
                            idempotency_key,
                            created_at
                        FROM support_questions
                        WHERE channel_id = :channelId
                        ORDER BY created_at ASC
                        """)
                .param("channelId", channelId)
                .query(this::mapSupportQuestion)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportQuestion> findByChannelIdAndCreatedAtBetween(
            UUID channelId,
            Instant windowStartInclusive,
            Instant windowEndExclusive
    ) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            channel_message_id,
                            channel_id,
                            sender_user_id,
                            body,
                            status,
                            idempotency_key,
                            created_at
                        FROM support_questions
                        WHERE channel_id = :channelId
                        AND created_at >= :windowStartInclusive
                        AND created_at < :windowEndExclusive
                        ORDER BY created_at ASC
                        """)
                .param("channelId", channelId)
                .param("windowStartInclusive", Timestamp.from(windowStartInclusive))
                .param("windowEndExclusive", Timestamp.from(windowEndExclusive))
                .query(this::mapSupportQuestion)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportQuestion> findByChannelIdAndStatus(UUID channelId, SupportQuestionStatus status) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            channel_message_id,
                            channel_id,
                            sender_user_id,
                            body,
                            status,
                            idempotency_key,
                            created_at
                        FROM support_questions
                        WHERE channel_id = :channelId
                        AND status = :status
                        ORDER BY created_at ASC
                        """)
                .param("channelId", channelId)
                .param("status", status.name())
                .query(this::mapSupportQuestion)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportQuestion> findByChannelIdAndSenderUserIdAndStatus(
            UUID channelId,
            UUID senderUserId,
            SupportQuestionStatus status
    ) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            channel_message_id,
                            channel_id,
                            sender_user_id,
                            body,
                            status,
                            idempotency_key,
                            created_at
                        FROM support_questions
                        WHERE channel_id = :channelId
                        AND sender_user_id = :senderUserId
                        AND status = :status
                        ORDER BY created_at ASC
                        """)
                .param("channelId", channelId)
                .param("senderUserId", senderUserId)
                .param("status", status.name())
                .query(this::mapSupportQuestion)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupportQuestion> findByIdAndChannelId(UUID channelId, UUID supportQuestionId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            channel_message_id,
                            channel_id,
                            sender_user_id,
                            body,
                            status,
                            idempotency_key,
                            created_at
                        FROM support_questions
                        WHERE channel_id = :channelId
                        AND id = :supportQuestionId
                        """)
                .param("channelId", channelId)
                .param("supportQuestionId", supportQuestionId)
                .query(this::mapSupportQuestion)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findIdsByChannelIdAndIds(UUID channelId, List<UUID> supportQuestionIds) {
        if (supportQuestionIds.isEmpty()) {
            return Set.of();
        }

        List<UUID> foundIds = jdbcClient.sql("""
                        SELECT id
                        FROM support_questions
                        WHERE channel_id = :channelId
                        AND id IN (:supportQuestionIds)
                        """)
                .param("channelId", channelId)
                .param("supportQuestionIds", supportQuestionIds)
                .query(UUID.class)
                .list();

        return new HashSet<>(foundIds);
    }

    @Override
    @Transactional
    public boolean updateStatus(
            UUID supportQuestionId,
            SupportQuestionStatus fromStatus,
            SupportQuestionStatus toStatus
    ) {
        return jdbcClient.sql("""
                        UPDATE support_questions
                        SET status = :toStatus
                        WHERE id = :supportQuestionId
                        AND status = :fromStatus
                        """)
                .param("supportQuestionId", supportQuestionId)
                .param("fromStatus", fromStatus.name())
                .param("toStatus", toStatus.name())
                .update() > 0;
    }

    private SupportQuestion mapSupportQuestion(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SupportQuestion(
                rs.getObject("id", UUID.class),
                rs.getObject("channel_message_id", UUID.class),
                rs.getObject("channel_id", UUID.class),
                rs.getObject("sender_user_id", UUID.class),
                rs.getString("body"),
                SupportQuestionStatus.valueOf(rs.getString("status")),
                rs.getString("idempotency_key"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }
}
