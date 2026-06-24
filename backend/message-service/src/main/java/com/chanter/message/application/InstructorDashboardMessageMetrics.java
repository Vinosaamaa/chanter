package com.chanter.message.application;

public record InstructorDashboardMessageMetrics(
        int unansweredSupportQuestions,
        int openTaQueueItems,
        int approvedFaqCount,
        int faqCandidateGroups
) {
}
