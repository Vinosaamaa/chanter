package com.chanter.community.domain;

import java.util.UUID;

public record CohortOfficeHoursAccess(
        UUID cohortId,
        UUID courseId,
        UUID studyServerId,
        boolean canScheduleOfficeHours,
        boolean canJoinOfficeHours,
        boolean canManageOfficeHours
) {
}
