package com.chanter.community.api;

import com.chanter.community.domain.CommunityDashboardMetrics;

public record InstructorDashboardCommunityMetricsResponse(
        java.util.UUID studyServerId,
        int liveOfficeHoursSessions,
        int scheduledOfficeHoursSessions,
        int officeHoursWaitlistEntries
) {
    static InstructorDashboardCommunityMetricsResponse from(CommunityDashboardMetrics metrics) {
        return new InstructorDashboardCommunityMetricsResponse(
                metrics.studyServerId(),
                metrics.liveOfficeHoursSessions(),
                metrics.scheduledOfficeHoursSessions(),
                metrics.officeHoursWaitlistEntries()
        );
    }
}
