package com.chanter.community.domain;

import java.util.List;
import java.util.UUID;

public record StudyAssistantViewerScope(
        UUID studyServerId,
        boolean canViewAllGrants,
        List<UUID> enrolledCourseIds,
        List<UUID> enrolledCohortIds,
        List<UUID> accessibleCourseChannelIds
) {
}
