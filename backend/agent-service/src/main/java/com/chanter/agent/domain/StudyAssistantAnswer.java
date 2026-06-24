package com.chanter.agent.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudyAssistantAnswer(
        UUID id,
        UUID supportQuestionId,
        UUID channelId,
        UUID studyServerId,
        UUID learnerUserId,
        String questionBody,
        String answerBody,
        AnswerConfidence confidence,
        boolean handoffRecommended,
        List<StudyAssistantAnswerSource> sources,
        Instant createdAt
) {
}
