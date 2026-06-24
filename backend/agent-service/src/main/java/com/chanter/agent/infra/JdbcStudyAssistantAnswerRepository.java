package com.chanter.agent.infra;

import com.chanter.agent.application.StudyAssistantAnswerRepository;
import com.chanter.agent.domain.AnswerConfidence;
import com.chanter.agent.domain.InvocationType;
import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.agent.domain.StudyAssistantAnswerSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcStudyAssistantAnswerRepository implements StudyAssistantAnswerRepository {

    private final JdbcClient jdbcClient;

    public JdbcStudyAssistantAnswerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<StudyAssistantAnswer> findBySupportQuestionId(UUID supportQuestionId) {
        List<StudyAssistantAnswer> answers = jdbcClient.sql("""
                        SELECT
                            id,
                            support_question_id,
                            channel_id,
                            study_server_id,
                            learner_user_id,
                            question_body,
                            answer_body,
                            confidence,
                            handoff_recommended,
                            created_at
                        FROM study_assistant_answers
                        WHERE support_question_id = :supportQuestionId
                        """)
                .param("supportQuestionId", supportQuestionId)
                .query(this::mapAnswerRow)
                .list();

        if (answers.isEmpty()) {
            return Optional.empty();
        }

        StudyAssistantAnswer answer = answers.getFirst();
        List<StudyAssistantAnswerSource> sources = jdbcClient.sql("""
                        SELECT id, resource_id, resource_title, excerpt
                        FROM study_assistant_answer_sources
                        WHERE answer_id = :answerId
                        ORDER BY resource_title
                        """)
                .param("answerId", answer.id())
                .query(this::mapSourceRow)
                .list();

        return Optional.of(new StudyAssistantAnswer(
                answer.id(),
                answer.supportQuestionId(),
                answer.channelId(),
                answer.studyServerId(),
                answer.learnerUserId(),
                answer.questionBody(),
                answer.answerBody(),
                answer.confidence(),
                answer.handoffRecommended(),
                sources,
                answer.createdAt()
        ));
    }

    @Override
    @Transactional
    public StudyAssistantAnswer saveAnswer(StudyAssistantAnswer answer, InvocationType invocationType) {
        try {
            insertAnswer(answer, invocationType);
        } catch (DuplicateKeyException exception) {
            return findBySupportQuestionId(answer.supportQuestionId())
                    .orElseThrow(() -> exception);
        }

        return new StudyAssistantAnswer(
                answer.id(),
                answer.supportQuestionId(),
                answer.channelId(),
                answer.studyServerId(),
                answer.learnerUserId(),
                answer.questionBody(),
                answer.answerBody(),
                answer.confidence(),
                answer.handoffRecommended(),
                List.copyOf(answer.sources()),
                answer.createdAt()
        );
    }

    private void insertAnswer(StudyAssistantAnswer answer, InvocationType invocationType) {
        jdbcClient.sql("""
                        INSERT INTO study_assistant_answers (
                            id,
                            support_question_id,
                            channel_id,
                            study_server_id,
                            learner_user_id,
                            question_body,
                            answer_body,
                            confidence,
                            handoff_recommended,
                            created_at
                        )
                        VALUES (
                            :id,
                            :supportQuestionId,
                            :channelId,
                            :studyServerId,
                            :learnerUserId,
                            :questionBody,
                            :answerBody,
                            :confidence,
                            :handoffRecommended,
                            :createdAt
                        )
                        """)
                .param("id", answer.id())
                .param("supportQuestionId", answer.supportQuestionId())
                .param("channelId", answer.channelId())
                .param("studyServerId", answer.studyServerId())
                .param("learnerUserId", answer.learnerUserId())
                .param("questionBody", answer.questionBody())
                .param("answerBody", answer.answerBody())
                .param("confidence", answer.confidence().name())
                .param("handoffRecommended", answer.handoffRecommended())
                .param("createdAt", OffsetDateTime.ofInstant(answer.createdAt(), ZoneOffset.UTC))
                .update();

        for (StudyAssistantAnswerSource source : answer.sources()) {
            jdbcClient.sql("""
                            INSERT INTO study_assistant_answer_sources (
                                id,
                                answer_id,
                                resource_id,
                                resource_title,
                                excerpt
                            )
                            VALUES (:id, :answerId, :resourceId, :resourceTitle, :excerpt)
                            """)
                    .param("id", source.id())
                    .param("answerId", answer.id())
                    .param("resourceId", source.resourceId())
                    .param("resourceTitle", source.resourceTitle())
                    .param("excerpt", source.excerpt())
                    .update();
        }

        jdbcClient.sql("""
                        INSERT INTO study_assistant_audit_records (
                            id,
                            answer_id,
                            study_server_id,
                            channel_id,
                            learner_user_id,
                            invocation_type,
                            confidence,
                            source_count,
                            created_at
                        )
                        VALUES (
                            :id,
                            :answerId,
                            :studyServerId,
                            :channelId,
                            :learnerUserId,
                            :invocationType,
                            :confidence,
                            :sourceCount,
                            :createdAt
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("answerId", answer.id())
                .param("studyServerId", answer.studyServerId())
                .param("channelId", answer.channelId())
                .param("learnerUserId", answer.learnerUserId())
                .param("invocationType", invocationType.name())
                .param("confidence", answer.confidence().name())
                .param("sourceCount", answer.sources().size())
                .param("createdAt", OffsetDateTime.ofInstant(answer.createdAt(), ZoneOffset.UTC))
                .update();
    }

    private StudyAssistantAnswer mapAnswerRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new StudyAssistantAnswer(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("support_question_id", UUID.class),
                resultSet.getObject("channel_id", UUID.class),
                resultSet.getObject("study_server_id", UUID.class),
                resultSet.getObject("learner_user_id", UUID.class),
                resultSet.getString("question_body"),
                resultSet.getString("answer_body"),
                AnswerConfidence.valueOf(resultSet.getString("confidence")),
                resultSet.getBoolean("handoff_recommended"),
                List.of(),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }

    private StudyAssistantAnswerSource mapSourceRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new StudyAssistantAnswerSource(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("resource_id", UUID.class),
                resultSet.getString("resource_title"),
                resultSet.getString("excerpt")
        );
    }
}
