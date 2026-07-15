package com.chanter.agent.api;

import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.agent.domain.StudyAssistantAnswerAudit;
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
        Instant createdAt,
        AuditResponse audit,
        boolean helpfulMarked,
        int helpfulCount
) {

    public static AssistantAnswerResponse from(
            StudyAssistantAnswer answer,
            String supportQuestionStatus,
            StudyAssistantAnswerAudit audit,
            boolean helpfulMarked,
            int helpfulCount
    ) {
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
                answer.createdAt(),
                audit == null ? null : AuditResponse.from(audit),
                helpfulMarked,
                helpfulCount
        );
    }

    public static AssistantAnswerResponse from(StudyAssistantAnswer answer, String supportQuestionStatus) {
        return from(answer, supportQuestionStatus, null, false, 0);
    }

    public record SourceResponse(UUID resourceId, String resourceTitle, String excerpt) {

        public static SourceResponse from(StudyAssistantAnswerSource source) {
            return new SourceResponse(source.resourceId(), source.resourceTitle(), source.excerpt());
        }
    }

    public record AuditResponse(
            String invocationType,
            int sourceCount,
            boolean llmUsed,
            String llmProvider,
            String llmModel,
            Instant createdAt
    ) {
        public static AuditResponse from(StudyAssistantAnswerAudit audit) {
            return new AuditResponse(
                    audit.invocationType(),
                    audit.sourceCount(),
                    audit.llmUsed(),
                    audit.llmProvider(),
                    audit.llmModel(),
                    audit.createdAt()
            );
        }
    }
}
