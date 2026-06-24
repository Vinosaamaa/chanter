package com.chanter.community.domain;

import java.util.UUID;

public record CommunityDashboardMetrics(
        UUID studyServerId,
        int liveOfficeHoursSessions,
        int scheduledOfficeHoursSessions,
        int officeHoursWaitlistEntries
) {
}
