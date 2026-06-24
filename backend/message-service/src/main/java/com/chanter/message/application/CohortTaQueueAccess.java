package com.chanter.message.application;

import java.util.UUID;

public record CohortTaQueueAccess(
        UUID cohortId,
        UUID courseId,
        UUID studyServerId,
        boolean canAddToTaQueue,
        boolean canManageTaQueue
) {
}
