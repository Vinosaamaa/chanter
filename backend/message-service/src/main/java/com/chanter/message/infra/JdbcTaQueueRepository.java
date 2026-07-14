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
        OffsetDateTime updatedAt = OffsetDateTime.ofInstant(item.updatedAt(), ZoneOffset.UTC);

        jdbcClient.sql("""
                        INSERT INTO ta_queue_items (
                            id,
                            cohort_id,
                            support_question_id,
                            channel_id,
                            learner_user_id,
                            body,
                            status,
                            assigned_ta_user_id,
                            created_at,
                            updated_at
                        )
                        VALUES (
                            :id,
                            :cohortId,
                            :supportQuestionId,
                            :channelId,
                            :learnerUserId,
                            :body,
                            :status,
                            :assignedTaUserId,
                            :createdAt,
                            :updatedAt
                        )
                        """)
                .param("id", item.id())
                .param("cohortId", item.cohortId())
                .param("supportQuestionId", item.supportQuestionId())
                .param("channelId", item.channelId())
                .param("learnerUserId", item.learnerUserId())
                .param("body", item.body())
                .param("status", item.status().name())
                .param("assignedTaUserId", item.assignedTaUserId())
                .param("createdAt", createdAt)
                .param("updatedAt", updatedAt)
                .update();

        return item;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaQueueItem> findByIdAndCohortId(UUID itemId, UUID cohortId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            cohort_id,
                            support_question_id,
                            channel_id,
                            learner_user_id,
                            body,
                            status,
                            assigned_ta_user_id,
                            created_at,
                            updated_at
                        FROM ta_queue_items
                        WHERE id = :itemId
                        AND cohort_id = :cohortId
                        """)
                .param("itemId", itemId)
                .param("cohortId", cohortId)
                .query(this::mapTaQueueItem)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaQueueItem> findByCohortIdAndStatuses(UUID cohortId, List<TaQueueItemStatus> statuses) {
        List<String> statusNames = statuses.stream().map(TaQueueItemStatus::name).toList();
        return jdbcClient.sql("""
                        SELECT
                            id,
                            cohort_id,
                            support_question_id,
                            channel_id,
                            learner_user_id,
                            body,
                            status,
                            assigned_ta_user_id,
                            created_at,
                            updated_at
                        FROM ta_queue_items
                        WHERE cohort_id = :cohortId
                        AND status IN (:statuses)
                        ORDER BY created_at ASC
                        """)
                .param("cohortId", cohortId)
                .param("statuses", statusNames)
                .query(this::mapTaQueueItem)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveBySupportQuestionId(UUID supportQuestionId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM ta_queue_items
                        WHERE support_question_id = :supportQuestionId
                        AND status IN ('OPEN', 'PICKED_UP')
                        """)
                .param("supportQuestionId", supportQuestionId)
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    @Transactional
    public int closeActiveBySupportQuestionId(
            UUID supportQuestionId,
            TaQueueItemStatus status,
            Instant updatedAt
    ) {
        return jdbcClient.sql("""
                        UPDATE ta_queue_items
                        SET status = :status,
                            updated_at = :updatedAt
                        WHERE support_question_id = :supportQuestionId
                        AND status IN ('OPEN', 'PICKED_UP')
                        """)
                .param("supportQuestionId", supportQuestionId)
                .param("status", status.name())
                .param("updatedAt", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
                .update();
    }

    @Override
    @Transactional
    public boolean updateStatus(
            UUID itemId,
            UUID cohortId,
            TaQueueItemStatus fromStatus,
            TaQueueItemStatus toStatus,
            UUID assignedTaUserId,
            Instant updatedAt
    ) {
        return jdbcClient.sql("""
                        UPDATE ta_queue_items
                        SET status = :toStatus,
                            assigned_ta_user_id = :assignedTaUserId,
                            updated_at = :updatedAt
                        WHERE id = :itemId
                        AND cohort_id = :cohortId
                        AND status = :fromStatus
                        """)
                .param("itemId", itemId)
                .param("cohortId", cohortId)
                .param("fromStatus", fromStatus.name())
                .param("toStatus", toStatus.name())
                .param("assignedTaUserId", assignedTaUserId)
                .param("updatedAt", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
                .update() > 0;
    }

    private TaQueueItem mapTaQueueItem(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new TaQueueItem(
                rs.getObject("id", UUID.class),
                rs.getObject("cohort_id", UUID.class),
                rs.getObject("support_question_id", UUID.class),
                rs.getObject("channel_id", UUID.class),
                rs.getObject("learner_user_id", UUID.class),
                rs.getString("body"),
                TaQueueItemStatus.valueOf(rs.getString("status")),
                rs.getObject("assigned_ta_user_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }
}
