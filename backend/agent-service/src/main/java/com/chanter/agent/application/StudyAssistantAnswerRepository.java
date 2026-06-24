package com.chanter.agent.application;

import com.chanter.agent.domain.AnswerConfidence;
import com.chanter.agent.domain.InvocationType;
import com.chanter.agent.domain.StudyAssistantAnswer;
import java.util.List;

public interface StudyAssistantAnswerRepository {

    StudyAssistantAnswer saveAnswer(
            StudyAssistantAnswer answer,
            InvocationType invocationType,
            int sourceCount
    );
}
