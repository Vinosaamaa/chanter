package com.chanter.community.api;

import com.chanter.community.domain.CohortTaQueueAccess;
import java.util.UUID;

public record CohortTaQueueAccessResponse(
        UUID cohortId,
        UUID courseId,
        UUID studyServerId,
        boolean canAddToTaQueue,
        boolean canManageTaQueue
) {

    public static CohortTaQueueAccessResponse from(CohortTaQueueAccess access) {
        return new CohortTaQueueAccessResponse(
                access.cohortId(),
                access.courseId(),
                access.studyServerId(),
                access.canAddToTaQueue(),
                access.canManageTaQueue()
        );
    }
}
