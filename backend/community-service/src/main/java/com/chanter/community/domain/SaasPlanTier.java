package com.chanter.community.domain;

public enum SaasPlanTier {
    STARTER(5),
    PRO(100),
    ORGANIZATION(1000);

    private final int aiInvocationLimit;

    SaasPlanTier(int aiInvocationLimit) {
        this.aiInvocationLimit = aiInvocationLimit;
    }

    public int aiInvocationLimit() {
        return aiInvocationLimit;
    }
}
