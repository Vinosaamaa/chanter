package com.chanter.message.infra;

import com.chanter.message.application.ApprovedFaqRepository;
import com.chanter.message.domain.ApprovedFaq;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcApprovedFaqRepository implements ApprovedFaqRepository {

    private final JdbcClient jdbcClient;

    public JdbcApprovedFaqRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public ApprovedFaq save(ApprovedFaq approvedFaq, List<UUID> sourceSupportQuestionIds) {
        OffsetDateTime createdAt = OffsetDateTime.ofInstant(approvedFaq.createdAt(), ZoneOffset.UTC);
        OffsetDateTime updatedAt = OffsetDateTime.ofInstant(approvedFaq.updatedAt(), ZoneOffset.UTC);

        jdbcClient.sql("""
                        INSERT INTO approved_faqs (
                            id,
                            course_id,
                            question,
                            answer,
                            approved_by_user_id,
                            created_at,
                            updated_at
                        )
                        VALUES (
                            :id,
                            :courseId,
                            :question,
                            :answer,
                            :approvedByUserId,
                            :createdAt,
                            :updatedAt
                        )
                        """)
                .param("id", approvedFaq.id())
                .param("courseId", approvedFaq.courseId())
                .param("question", approvedFaq.question())
                .param("answer", approvedFaq.answer())
                .param("approvedByUserId", approvedFaq.approvedByUserId())
                .param("createdAt", createdAt)
                .param("updatedAt", updatedAt)
                .update();

        replaceSourceQuestions(approvedFaq.id(), sourceSupportQuestionIds);

        return findByIdAndCourseId(approvedFaq.id(), approvedFaq.courseId())
                .orElseThrow(() -> new IllegalStateException("Approved FAQ was not persisted"));
    }

    @Override
    @Transactional
    public ApprovedFaq update(ApprovedFaq approvedFaq, List<UUID> sourceSupportQuestionIds) {
        OffsetDateTime updatedAt = OffsetDateTime.ofInstant(approvedFaq.updatedAt(), ZoneOffset.UTC);

        int updated = jdbcClient.sql("""
                        UPDATE approved_faqs
                        SET question = :question,
                            answer = :answer,
                            approved_by_user_id = :approvedByUserId,
                            updated_at = :updatedAt
                        WHERE id = :id
                        AND course_id = :courseId
                        """)
                .param("id", approvedFaq.id())
                .param("courseId", approvedFaq.courseId())
                .param("question", approvedFaq.question())
                .param("answer", approvedFaq.answer())
                .param("approvedByUserId", approvedFaq.approvedByUserId())
                .param("updatedAt", updatedAt)
                .update();

        if (updated == 0) {
            throw new IllegalStateException("Approved FAQ was not updated");
        }

        replaceSourceQuestions(approvedFaq.id(), sourceSupportQuestionIds);

        return findByIdAndCourseId(approvedFaq.id(), approvedFaq.courseId())
                .orElseThrow(() -> new IllegalStateException("Approved FAQ was not found after update"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApprovedFaq> findByIdAndCourseId(UUID approvedFaqId, UUID courseId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            course_id,
                            question,
                            answer,
                            approved_by_user_id,
                            created_at,
                            updated_at
                        FROM approved_faqs
                        WHERE id = :approvedFaqId
                        AND course_id = :courseId
                        """)
                .param("approvedFaqId", approvedFaqId)
                .param("courseId", courseId)
                .query(this::mapApprovedFaq)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovedFaq> findByCourseId(UUID courseId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            course_id,
                            question,
                            answer,
                            approved_by_user_id,
                            created_at,
                            updated_at
                        FROM approved_faqs
                        WHERE course_id = :courseId
                        ORDER BY updated_at DESC, question ASC
                        """)
                .param("courseId", courseId)
                .query(this::mapApprovedFaq)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovedFaq> searchByCourseId(UUID courseId, String query) {
        String normalizedQuery = query.trim().toLowerCase();
        if (normalizedQuery.isEmpty()) {
            return findByCourseId(courseId);
        }

        String pattern = "%" + normalizedQuery + "%";
        return jdbcClient.sql("""
                        SELECT
                            id,
                            course_id,
                            question,
                            answer,
                            approved_by_user_id,
                            created_at,
                            updated_at
                        FROM approved_faqs
                        WHERE course_id = :courseId
                        AND (
                            LOWER(question) LIKE :pattern
                            OR LOWER(answer) LIKE :pattern
                        )
                        ORDER BY updated_at DESC, question ASC
                        """)
                .param("courseId", courseId)
                .param("pattern", pattern)
                .query(this::mapApprovedFaq)
                .list();
    }

    private void replaceSourceQuestions(UUID approvedFaqId, List<UUID> sourceSupportQuestionIds) {
        jdbcClient.sql("DELETE FROM approved_faq_source_questions WHERE approved_faq_id = :approvedFaqId")
                .param("approvedFaqId", approvedFaqId)
                .update();

        for (UUID supportQuestionId : sourceSupportQuestionIds) {
            jdbcClient.sql("""
                            INSERT INTO approved_faq_source_questions (approved_faq_id, support_question_id)
                            VALUES (:approvedFaqId, :supportQuestionId)
                            """)
                    .param("approvedFaqId", approvedFaqId)
                    .param("supportQuestionId", supportQuestionId)
                    .update();
        }
    }

    private ApprovedFaq mapApprovedFaq(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ApprovedFaq(
                rs.getObject("id", UUID.class),
                rs.getObject("course_id", UUID.class),
                rs.getString("question"),
                rs.getString("answer"),
                rs.getObject("approved_by_user_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }
}
