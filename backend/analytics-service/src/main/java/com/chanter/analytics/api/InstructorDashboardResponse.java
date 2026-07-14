package com.chanter.analytics.api;

import java.util.List;
import java.util.UUID;

public record InstructorDashboardResponse(
        UUID studyServerId,
        String planTier,
        int unansweredSupportQuestions,
        int repeatedQuestionGroups,
        int approvedFaqCount,
        int openTaQueueItems,
        int liveOfficeHoursSessions,
        int scheduledOfficeHoursSessions,
        int officeHoursWaitlistEntries,
        int aiInvocationCount,
        int aiInvocationLimit,
        int remainingAiInvocations,
        boolean quotaExhausted,
        int lowConfidenceHandoffs,
        List<TeachingCourseResponse> courses
) {
    public record TeachingCourseResponse(
            UUID courseId,
            String title,
            UUID questionChannelId,
            List<TeachingCohortResponse> cohorts,
            int unansweredSupportQuestions,
            int repeatedQuestionGroups,
            int approvedFaqCount,
            int openTaQueueItems
    ) {
    }

    public record TeachingCohortResponse(UUID cohortId, String name, int openTaQueueItems) {
    }
}
