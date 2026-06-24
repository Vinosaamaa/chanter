package com.chanter.message.infra;

import com.chanter.message.application.InstructorDashboardMetricsRepository;
import com.chanter.message.domain.SupportQuestionStatus;
import com.chanter.message.domain.TaQueueItemStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcInstructorDashboardMetricsRepository implements InstructorDashboardMetricsRepository {

    private final JdbcClient jdbcClient;

    public JdbcInstructorDashboardMetricsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public int countUnansweredSupportQuestions(List<UUID> questionChannelIds) {
        if (questionChannelIds.isEmpty()) {
            return 0;
        }

        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM support_questions
                        WHERE channel_id IN (:channelIds)
                        AND status IN (:unansweredStatus, :lowConfidenceStatus)
                        """)
                .param("channelIds", questionChannelIds)
                .param("unansweredStatus", SupportQuestionStatus.UNANSWERED.name())
                .param("lowConfidenceStatus", SupportQuestionStatus.AI_LOW_CONFIDENCE.name())
                .query(Integer.class)
                .single();
    }

    @Override
    @Transactional(readOnly = true)
    public int countOpenTaQueueItems(List<UUID> cohortIds) {
        if (cohortIds.isEmpty()) {
            return 0;
        }

        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM ta_queue_items
                        WHERE cohort_id IN (:cohortIds)
                        AND status = :openStatus
                        """)
                .param("cohortIds", cohortIds)
                .param("openStatus", TaQueueItemStatus.OPEN.name())
                .query(Integer.class)
                .single();
    }

    @Override
    @Transactional(readOnly = true)
    public int countApprovedFaqs(List<UUID> courseIds) {
        if (courseIds.isEmpty()) {
            return 0;
        }

        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM approved_faqs
                        WHERE course_id IN (:courseIds)
                        """)
                .param("courseIds", courseIds)
                .query(Integer.class)
                .single();
    }
}
