package com.chanter.message.api;

import com.chanter.message.application.FaqCandidateGroup;
import com.chanter.message.domain.SupportQuestion;
import java.util.List;

public record FaqCandidateGroupResponse(
        String representativeQuestion,
        List<SupportQuestionSummaryResponse> supportQuestions
) {

    public static FaqCandidateGroupResponse from(FaqCandidateGroup group) {
        List<SupportQuestionSummaryResponse> supportQuestions = group.supportQuestions().stream()
                .map(FaqCandidateGroupResponse::toSummary)
                .toList();
        return new FaqCandidateGroupResponse(group.representativeQuestion(), supportQuestions);
    }

    private static SupportQuestionSummaryResponse toSummary(SupportQuestion supportQuestion) {
        return new SupportQuestionSummaryResponse(
                supportQuestion.id(),
                supportQuestion.channelMessageId(),
                supportQuestion.channelId(),
                supportQuestion.senderUserId(),
                supportQuestion.body(),
                supportQuestion.status().name(),
                supportQuestion.createdAt()
        );
    }
}
