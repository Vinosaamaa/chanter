package com.chanter.agent.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface StudyAssistantGrantCandidatesClient {

    GrantCandidates requireGrantCandidates(UUID studyServerId, UUID userId);

    ViewerScope requireViewerScope(UUID studyServerId, UUID userId);

    record ChannelCandidate(UUID id, String name, String kind) {
    }

    record CohortCandidate(UUID id, String name) {
    }

    record CourseCandidate(
            UUID id,
            String title,
            List<CohortCandidate> cohorts,
            List<ChannelCandidate> channels
    ) {
    }

    record GrantCandidates(
            UUID studyServerId,
            List<ChannelCandidate> studyServerChannels,
            List<CourseCandidate> courses
    ) {
    }

    record ViewerScope(
            UUID studyServerId,
            boolean canViewAllGrants,
            Set<UUID> enrolledCourseIds,
            Set<UUID> enrolledCohortIds,
            Set<UUID> accessibleCourseChannelIds
    ) {
    }
}
