package com.chanter.message.application;

import java.util.UUID;

public record CohortTaQueueAccess(
        UUID cohortId,
        UUID courseId,
        boolean canAddToTaQueue,
        boolean canManageTaQueue
) {
}
