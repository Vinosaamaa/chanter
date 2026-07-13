package com.chanter.community.domain;

public record CohortCapabilities(
        boolean enrolled,
        boolean teachingAssistant,
        boolean canManage
) {
}
