package com.chanter.agent.application;

import com.chanter.agent.domain.InvocationType;
import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.common.events.AcceptedAnswerStatus;
import com.chanter.common.events.DurableOutbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyAssistantAnswerPersistenceService {

    private final AiQuotaEnforcementService aiQuotaEnforcementService;
    private final StudyAssistantAnswerRepository answerRepository;
    private final DurableOutbox outbox;
    private final ObjectMapper mapper;

    public StudyAssistantAnswerPersistenceService(
            AiQuotaEnforcementService aiQuotaEnforcementService,
            StudyAssistantAnswerRepository answerRepository,
            DurableOutbox outbox,
            ObjectMapper mapper
    ) {
        this.aiQuotaEnforcementService = aiQuotaEnforcementService;
        this.answerRepository = answerRepository;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Transactional
    public StudyAssistantAnswer saveNativeAnswer(StudyAssistantAnswer answer, InvocationType invocationType, String model) {
        var saved = saveAnswer(answer, invocationType, "codex-native", model, true);
        var status = new AcceptedAnswerStatus(saved.id(), saved.supportQuestionId(), saved.channelId(), saved.learnerUserId(),
                saved.handoffRecommended() ? "AI_LOW_CONFIDENCE" : "AI_ANSWERED");
        try {
            outbox.append("message", AcceptedAnswerStatus.KIND, status.aggregateKey(), mapper.writeValueAsString(status));
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("Cannot encode accepted answer status", invalid);
        }
        return saved;
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
        return answerRepository.saveAnswer(answer, invocationType, llmProvider, llmModel, llmUsed);
    }
}
