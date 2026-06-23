package com.chanter.community.api;

import com.chanter.community.domain.StudyAssistantViewerScope;
import java.util.List;
import java.util.UUID;

public record StudyAssistantViewerScopeResponse(
        UUID studyServerId,
        boolean canViewAllGrants,
        List<UUID> enrolledCourseIds,
        List<UUID> enrolledCohortIds,
        List<UUID> accessibleCourseChannelIds
) {

    static StudyAssistantViewerScopeResponse from(StudyAssistantViewerScope scope) {
        return new StudyAssistantViewerScopeResponse(
                scope.studyServerId(),
                scope.canViewAllGrants(),
                scope.enrolledCourseIds(),
                scope.enrolledCohortIds(),
                scope.accessibleCourseChannelIds()
        );
    }
}
