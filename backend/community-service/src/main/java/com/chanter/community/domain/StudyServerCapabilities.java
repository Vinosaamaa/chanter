package com.chanter.community.domain;

public record StudyServerCapabilities(
        boolean owner,
        boolean canTeach,
        boolean canCreateCourse,
        boolean canManageCommunity,
        boolean canManageEvents,
        boolean canManageBilling
) {
}
