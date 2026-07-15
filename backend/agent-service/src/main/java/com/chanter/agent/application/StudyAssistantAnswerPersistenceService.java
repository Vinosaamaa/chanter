package com.chanter.agent.application;

import com.chanter.agent.domain.InvocationType;
import com.chanter.agent.domain.StudyAssistantAnswer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyAssistantAnswerPersistenceService {

    private final AiQuotaEnforcementService aiQuotaEnforcementService;
    private final StudyAssistantAnswerRepository answerRepository;

    public StudyAssistantAnswerPersistenceService(
            AiQuotaEnforcementService aiQuotaEnforcementService,
            StudyAssistantAnswerRepository answerRepository
    ) {
        this.aiQuotaEnforcementService = aiQuotaEnforcementService;
        this.answerRepository = answerRepository;
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
