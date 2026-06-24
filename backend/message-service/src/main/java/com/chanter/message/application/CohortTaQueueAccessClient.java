package com.chanter.message.application;

import java.util.UUID;

public interface CohortTaQueueAccessClient {

    CohortTaQueueAccess requireAccess(UUID cohortId, UUID userId);
}
