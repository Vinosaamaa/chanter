package com.chanter.community.api;

import com.chanter.community.domain.CohortEnrollment;
import java.time.Instant;
import java.util.UUID;

public record CohortEnrollmentResponse(
        UUID learnerUserId,
        UUID enrolledByUserId,
        Instant enrolledAt
) {

    static CohortEnrollmentResponse from(CohortEnrollment enrollment) {
        return new CohortEnrollmentResponse(
                enrollment.learnerUserId(),
                enrollment.enrolledByUserId(),
                enrollment.enrolledAt()
        );
    }
}
