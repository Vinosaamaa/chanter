package com.chanter.community.domain;

import java.time.Instant;
import java.util.UUID;

public record CohortEnrollment(
        UUID learnerUserId,
        UUID enrolledByUserId,
        Instant enrolledAt
) {
}
