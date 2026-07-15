package com.chanter.agent.application;

import com.chanter.agent.domain.InvocationType;
import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.agent.domain.StudyAssistantAnswerAudit;
import java.util.Optional;
import java.util.UUID;

public interface StudyAssistantAnswerRepository {

    Optional<StudyAssistantAnswer> findBySupportQuestionId(UUID supportQuestionId);

    Optional<StudyAssistantAnswerAudit> findAuditByAnswerId(UUID answerId);

    boolean markHelpful(UUID answerId, UUID userId);

    boolean isHelpfulMarked(UUID answerId, UUID userId);

    int countHelpful(UUID answerId);

    StudyAssistantAnswer saveAnswer(StudyAssistantAnswer answer, InvocationType invocationType);

    StudyAssistantAnswer saveAnswer(
            StudyAssistantAnswer answer,
            InvocationType invocationType,
            String llmProvider,
            String llmModel,
            boolean llmUsed
    );
}
