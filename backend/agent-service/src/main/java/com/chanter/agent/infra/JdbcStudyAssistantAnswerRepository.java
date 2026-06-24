package com.chanter.agent.infra;

import com.chanter.agent.application.StudyAssistantAnswerRepository;
import com.chanter.agent.domain.InvocationType;
import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.agent.domain.StudyAssistantAnswerSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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
    @Transactional
    public StudyAssistantAnswer saveAnswer(
            StudyAssistantAnswer answer,
            InvocationType invocationType,
            int sourceCount
    ) {
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
                .param("sourceCount", sourceCount)
                .param("createdAt", OffsetDateTime.ofInstant(answer.createdAt(), ZoneOffset.UTC))
                .update();

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
}
