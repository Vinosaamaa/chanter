package com.chanter.message.infra;

import com.chanter.message.application.TaQueueRepository;
import com.chanter.message.domain.TaQueueItem;
import com.chanter.message.domain.TaQueueItemStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcTaQueueRepository implements TaQueueRepository {

    private final JdbcClient jdbcClient;

    public JdbcTaQueueRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public TaQueueItem save(TaQueueItem item) {
        OffsetDateTime createdAt = OffsetDateTime.ofInstant(item.createdAt(), ZoneOffset.UTC);

        jdbcClient.sql("""
                        INSERT INTO ta_queue_items (
                            id,
                            cohort_id,
                            course_id,
                            channel_id,
                            support_question_id,
                            requester_user_id,
                            status,
                            picked_up_by_user_id,
                            resolved_by_user_id,
                            created_at,
                            picked_up_at,
                            resolved_at,
                            cancelled_at
                        )
                        VALUES (
                            :id,
                            :cohortId,
                            :courseId,
                            :channelId,
                            :supportQuestionId,
                            :requesterUserId,
                            :status,
                            :pickedUpByUserId,
                            :resolvedByUserId,
                            :createdAt,
                            :pickedUpAt,
                            :resolvedAt,
                            :cancelledAt
                        )
                        """)
                .param("id", item.id())
                .param("cohortId", item.cohortId())
                .param("courseId", item.courseId())
                .param("channelId", item.channelId())
                .param("supportQuestionId", item.supportQuestionId())
                .param("requesterUserId", item.requesterUserId())
                .param("status", item.status().name())
                .param("pickedUpByUserId", item.pickedUpByUserId())
                .param("resolvedByUserId", item.resolvedByUserId())
                .param("createdAt", createdAt)
                .param("pickedUpAt", toOffsetDateTime(item.pickedUpAt()))
                .param("resolvedAt", toOffsetDateTime(item.resolvedAt()))
                .param("cancelledAt", toOffsetDateTime(item.cancelledAt()))
                .update();

        return findByIdAndCohortId(item.id(), item.cohortId())
                .orElseThrow(() -> new IllegalStateException("TA Queue item was not persisted"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaQueueItem> findByIdAndCohortId(UUID itemId, UUID cohortId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            cohort_id,
                            course_id,
                            channel_id,
                            support_question_id,
                            requester_user_id,
                            status,
                            picked_up_by_user_id,
                            resolved_by_user_id,
                            created_at,
                            picked_up_at,
                            resolved_at,
                            cancelled_at
                        FROM ta_queue_items
                        WHERE id = :itemId
                        AND cohort_id = :cohortId
                        """)
                .param("itemId", itemId)
                .param("cohortId", cohortId)
                .query(this::mapItem)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaQueueItem> findActiveBySupportQuestionId(UUID supportQuestionId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            cohort_id,
                            course_id,
                            channel_id,
                            support_question_id,
                            requester_user_id,
                            status,
                            picked_up_by_user_id,
                            resolved_by_user_id,
                            created_at,
                            picked_up_at,
                            resolved_at,
                            cancelled_at
                        FROM ta_queue_items
                        WHERE support_question_id = :supportQuestionId
                        AND status IN ('OPEN', 'IN_PROGRESS')
                        """)
                .param("supportQuestionId", supportQuestionId)
                .query(this::mapItem)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaQueueItem> findOpenByCohortId(UUID cohortId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            cohort_id,
                            course_id,
                            channel_id,
                            support_question_id,
                            requester_user_id,
                            status,
                            picked_up_by_user_id,
                            resolved_by_user_id,
                            created_at,
                            picked_up_at,
                            resolved_at,
                            cancelled_at
                        FROM ta_queue_items
                        WHERE cohort_id = :cohortId
                        AND status IN ('OPEN', 'IN_PROGRESS')
                        ORDER BY created_at
                        """)
                .param("cohortId", cohortId)
                .query(this::mapItem)
                .list();
    }

    @Override
    @Transactional
    public boolean updateStatus(
            UUID itemId,
            TaQueueItemStatus expectedStatus,
            TaQueueItemStatus newStatus,
            UUID actorUserId,
            Instant timestamp
    ) {
        OffsetDateTime eventAt = OffsetDateTime.ofInstant(timestamp, ZoneOffset.UTC);
        int updated = switch (newStatus) {
            case IN_PROGRESS -> jdbcClient.sql("""
                            UPDATE ta_queue_items
                            SET status = :newStatus,
                                picked_up_by_user_id = :actorUserId,
                                picked_up_at = :eventAt
                            WHERE id = :itemId
                            AND status = :expectedStatus
                            """)
                    .param("newStatus", newStatus.name())
                    .param("actorUserId", actorUserId)
                    .param("eventAt", eventAt)
                    .param("itemId", itemId)
                    .param("expectedStatus", expectedStatus.name())
                    .update();
            case RESOLVED -> jdbcClient.sql("""
                            UPDATE ta_queue_items
                            SET status = :newStatus,
                                resolved_by_user_id = :actorUserId,
                                resolved_at = :eventAt
                            WHERE id = :itemId
                            AND status = :expectedStatus
                            """)
                    .param("newStatus", newStatus.name())
                    .param("actorUserId", actorUserId)
                    .param("eventAt", eventAt)
                    .param("itemId", itemId)
                    .param("expectedStatus", expectedStatus.name())
                    .update();
            case CANCELLED -> jdbcClient.sql("""
                            UPDATE ta_queue_items
                            SET status = :newStatus,
                                cancelled_at = :eventAt
                            WHERE id = :itemId
                            AND status = :expectedStatus
                            """)
                    .param("newStatus", newStatus.name())
                    .param("eventAt", eventAt)
                    .param("itemId", itemId)
                    .param("expectedStatus", expectedStatus.name())
                    .update();
            default -> 0;
        };
        return updated == 1;
    }

    private TaQueueItem mapItem(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new TaQueueItem(
                rs.getObject("id", UUID.class),
                rs.getObject("cohort_id", UUID.class),
                rs.getObject("course_id", UUID.class),
                rs.getObject("channel_id", UUID.class),
                rs.getObject("support_question_id", UUID.class),
                rs.getObject("requester_user_id", UUID.class),
                TaQueueItemStatus.valueOf(rs.getString("status")),
                rs.getObject("picked_up_by_user_id", UUID.class),
                rs.getObject("resolved_by_user_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                toInstant(rs.getTimestamp("picked_up_at")),
                toInstant(rs.getTimestamp("resolved_at")),
                toInstant(rs.getTimestamp("cancelled_at"))
        );
    }

    private static Instant toInstant(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
