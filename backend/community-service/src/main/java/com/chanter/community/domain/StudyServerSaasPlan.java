package com.chanter.community.domain;

import java.util.UUID;

public record StudyServerSaasPlan(
        UUID studyServerId,
        SaasPlanTier planTier,
        int aiInvocationLimit
) {
}
