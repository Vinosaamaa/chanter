package com.chanter.community.api;

import com.chanter.community.domain.CohortOfficeHoursAccess;

public record CohortOfficeHoursAccessResponse(
        java.util.UUID cohortId,
        java.util.UUID courseId,
        java.util.UUID studyServerId,
        boolean canScheduleOfficeHours,
        boolean canJoinOfficeHours,
        boolean canManageOfficeHours
) {
    public static CohortOfficeHoursAccessResponse from(CohortOfficeHoursAccess access) {
        return new CohortOfficeHoursAccessResponse(
                access.cohortId(),
                access.courseId(),
                access.studyServerId(),
                access.canScheduleOfficeHours(),
                access.canJoinOfficeHours(),
                access.canManageOfficeHours()
        );
    }
}
