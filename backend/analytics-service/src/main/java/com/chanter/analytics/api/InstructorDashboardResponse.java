package com.chanter.analytics.api;

import java.util.UUID;

public record InstructorDashboardResponse(
        UUID studyServerId,
        int unansweredSupportQuestions,
        int repeatedQuestionGroups,
        int approvedFaqCount,
        int openTaQueueItems,
        int liveOfficeHoursSessions,
        int scheduledOfficeHoursSessions,
        int officeHoursWaitlistEntries,
        int aiInvocationCount,
        int lowConfidenceHandoffs
) {
}
