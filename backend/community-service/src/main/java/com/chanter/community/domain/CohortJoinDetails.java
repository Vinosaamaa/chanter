package com.chanter.community.domain;

import java.util.UUID;

public record CohortJoinDetails(
        UUID studyServerId,
        UUID inviteCode,
        CohortEnrollmentPolicy enrollmentPolicy
) {
}
