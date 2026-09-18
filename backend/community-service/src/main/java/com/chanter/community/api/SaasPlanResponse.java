package com.chanter.community.api;

import com.chanter.community.domain.StudyServerSaasPlan;

public record SaasPlanResponse(
        java.util.UUID studyServerId,
        String planTier,
        int aiInvocationLimit,
        String entitlementSource,
        String usageWindow
) {
    static SaasPlanResponse from(StudyServerSaasPlan plan) {
        return new SaasPlanResponse(
                plan.studyServerId(),
                plan.planTier().name(),
                plan.aiInvocationLimit(),
                "OPERATOR_POLICY",
                "LIFETIME"
        );
    }
}
