package com.chanter.agent.application;

import java.util.UUID;

public interface StudyServerSaasPlanClient {

    StudyServerSaasPlan fetchPlan(UUID studyServerId, UUID actingUserId);

    record StudyServerSaasPlan(
            UUID studyServerId,
            String planTier,
            int aiInvocationLimit
    ) {
    }
}
