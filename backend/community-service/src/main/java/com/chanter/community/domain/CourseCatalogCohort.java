package com.chanter.community.domain;

import java.util.UUID;

public record CourseCatalogCohort(
        UUID id,
        String name,
        CohortEnrollmentPolicy enrollmentPolicy,
        boolean enrolled,
        int learnerCount
) {
}
