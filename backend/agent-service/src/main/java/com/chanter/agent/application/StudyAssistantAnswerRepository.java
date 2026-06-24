package com.chanter.agent.application;

import com.chanter.agent.domain.InvocationType;
import com.chanter.agent.domain.StudyAssistantAnswer;
import java.util.Optional;
import java.util.UUID;

public interface StudyAssistantAnswerRepository {

    Optional<StudyAssistantAnswer> findBySupportQuestionId(UUID supportQuestionId);

    StudyAssistantAnswer saveAnswer(StudyAssistantAnswer answer, InvocationType invocationType);
}
