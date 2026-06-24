package com.chanter.agent.api;

import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.agent.domain.StudyAssistantAnswerSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssistantAnswerResponse(
        UUID id,
        UUID supportQuestionId,
        UUID channelId,
        UUID studyServerId,
        UUID learnerUserId,
        String questionBody,
        String answerBody,
        String confidence,
        boolean handoffRecommended,
        String supportQuestionStatus,
        List<SourceResponse> sources,
        Instant createdAt
) {

    public static AssistantAnswerResponse from(StudyAssistantAnswer answer, String supportQuestionStatus) {
        return new AssistantAnswerResponse(
                answer.id(),
                answer.supportQuestionId(),
                answer.channelId(),
                answer.studyServerId(),
                answer.learnerUserId(),
                answer.questionBody(),
                answer.answerBody(),
                answer.confidence().name(),
                answer.handoffRecommended(),
                supportQuestionStatus,
                answer.sources().stream().map(SourceResponse::from).toList(),
                answer.createdAt()
        );
    }

    public record SourceResponse(UUID resourceId, String resourceTitle, String excerpt) {

        public static SourceResponse from(StudyAssistantAnswerSource source) {
            return new SourceResponse(source.resourceId(), source.resourceTitle(), source.excerpt());
        }
    }
}
