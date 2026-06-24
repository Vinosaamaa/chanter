package com.chanter.community.domain;

import java.util.UUID;

public record CohortTaQueueAccess(
        UUID cohortId,
        UUID courseId,
        boolean canAddToTaQueue,
        boolean canManageTaQueue
) {
}
