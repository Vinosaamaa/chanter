package com.chanter.agent.infra;

import com.chanter.agent.application.StudyAssistantGrantCandidatesClient;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("test")
public class TestStudyAssistantGrantCandidatesClient implements StudyAssistantGrantCandidatesClient {

    private final Map<String, GrantCandidates> grantCandidates = new HashMap<>();
    private final Map<String, ViewerScope> viewerScopes = new HashMap<>();

    public void registerGrantCandidates(UUID studyServerId, UUID userId, GrantCandidates candidates) {
        grantCandidates.put(key(studyServerId, userId), candidates);
    }

    public void registerViewerScope(UUID studyServerId, UUID userId, ViewerScope scope) {
        viewerScopes.put(key(studyServerId, userId), scope);
    }

    public void clear() {
        grantCandidates.clear();
        viewerScopes.clear();
    }

    @Override
    public GrantCandidates requireGrantCandidates(UUID studyServerId, UUID userId) {
        GrantCandidates candidates = grantCandidates.get(key(studyServerId, userId));
        if (candidates == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Grant candidates require Study Server Owner or Course Instructor role"
            );
        }
        return candidates;
    }

    @Override
    public ViewerScope requireViewerScope(UUID studyServerId, UUID userId) {
        ViewerScope scope = viewerScopes.get(key(studyServerId, userId));
        if (scope == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Study Assistant presence requires Study Server membership or enrollment"
            );
        }
        return scope;
    }

    public static ViewerScope instructorScope(UUID studyServerId) {
        return new ViewerScope(studyServerId, true, Set.of(), Set.of(), Set.of());
    }

    public static ViewerScope learnerScope(
            UUID studyServerId,
            Set<UUID> enrolledCourseIds,
            Set<UUID> enrolledCohortIds,
            Set<UUID> accessibleCourseChannelIds
    ) {
        return new ViewerScope(
                studyServerId,
                false,
                Set.copyOf(enrolledCourseIds),
                Set.copyOf(enrolledCohortIds),
                Set.copyOf(accessibleCourseChannelIds)
        );
    }

    public static GrantCandidates emptyCandidates(UUID studyServerId) {
        return new GrantCandidates(studyServerId, java.util.List.of(), java.util.List.of());
    }

    private static String key(UUID studyServerId, UUID userId) {
        return studyServerId + ":" + userId;
    }
}
