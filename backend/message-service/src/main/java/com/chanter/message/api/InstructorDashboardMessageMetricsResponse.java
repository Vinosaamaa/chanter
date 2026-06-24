package com.chanter.message.api;

import com.chanter.message.application.InstructorDashboardMessageMetrics;

public record InstructorDashboardMessageMetricsResponse(
        int unansweredSupportQuestions,
        int openTaQueueItems,
        int approvedFaqCount,
        int faqCandidateGroups
) {
    static InstructorDashboardMessageMetricsResponse from(InstructorDashboardMessageMetrics metrics) {
        return new InstructorDashboardMessageMetricsResponse(
                metrics.unansweredSupportQuestions(),
                metrics.openTaQueueItems(),
                metrics.approvedFaqCount(),
                metrics.faqCandidateGroups()
        );
    }
}
