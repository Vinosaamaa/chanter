package com.chanter.agent.application;

import com.chanter.agent.domain.InvocationType;
import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.common.events.AcceptedAnswerStatus;
import com.chanter.common.events.DurableOutbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyAssistantAnswerPersistenceService {

    private final AiQuotaEnforcementService aiQuotaEnforcementService;
    private final StudyAssistantAnswerRepository answerRepository;
    private final DurableOutbox outbox;
    private final ObjectMapper mapper;
    private final JdbcClient jdbc;

    public StudyAssistantAnswerPersistenceService(
            AiQuotaEnforcementService aiQuotaEnforcementService,
            StudyAssistantAnswerRepository answerRepository,
            DurableOutbox outbox,
            ObjectMapper mapper,
            JdbcClient jdbc
    ) {
        this.aiQuotaEnforcementService = aiQuotaEnforcementService;
        this.answerRepository = answerRepository;
        this.outbox = outbox;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @Transactional
    public void reconcileAnswerStatus(StudyAssistantAnswer answer) {
        // Serialize legacy-answer repair with concurrent readers without emitting duplicate status events.
        jdbc.sql("SELECT id FROM study_assistant_answers WHERE id=:id FOR UPDATE").param("id", answer.id()).query(java.util.UUID.class).single();
        if (jdbc.sql("SELECT COUNT(*) FROM study_assistant_answers WHERE id=:id AND status_event_id IS NOT NULL")
                .param("id",answer.id()).query(Integer.class).single() > 0) return;
        var status = new AcceptedAnswerStatus(answer.id(), answer.supportQuestionId(), answer.channelId(), answer.learnerUserId(),
                answer.handoffRecommended() ? "AI_LOW_CONFIDENCE" : "AI_ANSWERED");
        try {
            var event=outbox.append("message", AcceptedAnswerStatus.KIND, status.aggregateKey(), mapper.writeValueAsString(status));
            jdbc.sql("UPDATE study_assistant_answers SET status_event_id=:event WHERE id=:id")
                    .param("event",event).param("id",answer.id()).update();
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("Cannot encode accepted answer status", invalid);
        }
    }

    @Transactional
    public StudyAssistantAnswer saveAnswer(StudyAssistantAnswer answer, InvocationType invocationType) {
        return saveAnswer(answer, invocationType, null, null, false);
    }

    @Transactional
    public StudyAssistantAnswer saveAnswer(
            StudyAssistantAnswer answer,
            InvocationType invocationType,
            String llmProvider,
            String llmModel,
            boolean llmUsed
    ) {
        aiQuotaEnforcementService.requireQuotaAvailableUnderLock(answer.studyServerId(), answer.learnerUserId());
        var saved = answerRepository.saveAnswer(answer, invocationType, llmProvider, llmModel, llmUsed);
        reconcileAnswerStatus(saved);
        return saved;
    }
}
