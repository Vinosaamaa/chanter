package com.chanter.agent.application;

import com.chanter.agent.application.CourseResourceCatalogClient.CourseResourceSummary;
import com.chanter.agent.domain.GrantType;
import com.chanter.agent.domain.StudyAssistantGrant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves Study Assistant install grants into a tool-safe scope for MCP-compatible tools.
 */
@Service
public class AssistantGrantScopeService {

    private final StudyAssistantService studyAssistantService;
    private final CourseResourceCatalogClient courseResourceCatalogClient;

    public AssistantGrantScopeService(
            StudyAssistantService studyAssistantService,
            CourseResourceCatalogClient courseResourceCatalogClient
    ) {
        this.studyAssistantService = studyAssistantService;
        this.courseResourceCatalogClient = courseResourceCatalogClient;
    }

    public GrantScope requireScope(UUID studyServerId, UUID courseId, UUID viewerUserId, UUID channelId) {
        StudyAssistantService.Presence presence = studyAssistantService.findPresence(studyServerId, viewerUserId);
        if (!presence.installed()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Study Assistant is not installed");
        }

        boolean courseGranted = presence.grants().stream()
                .anyMatch(grant -> grant.grantType() == GrantType.COURSE
                        && grant.grantTargetId().equals(courseId));
        Set<UUID> grantedResourceIds = presence.grants().stream()
                .filter(grant -> grant.grantType() == GrantType.COURSE_RESOURCE)
                .map(StudyAssistantGrant::grantTargetId)
                .collect(Collectors.toSet());

        Map<UUID, CourseResourceSummary> grantedResources = new LinkedHashMap<>();
        for (CourseResourceSummary resource : courseResourceCatalogClient.listAiApprovedCourseResources(
                courseId,
                viewerUserId
        )) {
            if (grantedResourceIds.contains(resource.id())) {
                grantedResources.put(resource.id(), resource);
            }
        }

        if (!courseGranted && grantedResources.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No Study Assistant grants cover this course"
            );
        }

        if (channelId != null) {
            boolean channelGranted = presence.grants().stream()
                    .anyMatch(grant -> grant.grantType() == GrantType.COURSE_CHANNEL
                            && grant.grantTargetId().equals(channelId));
            if (!channelGranted) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Course channel is not granted to the Study Assistant"
                );
            }
        }

        return new GrantScope(
                studyServerId,
                courseId,
                viewerUserId,
                channelId,
                Set.copyOf(grantedResources.keySet()),
                List.copyOf(grantedResources.values()),
                presence.grants()
        );
    }

    public void requireResourceGranted(GrantScope scope, UUID resourceId) {
        if (!scope.grantedResourceIds().contains(resourceId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Resource is outside Study Assistant grants"
            );
        }
    }

    public record GrantScope(
            UUID studyServerId,
            UUID courseId,
            UUID viewerUserId,
            UUID channelId,
            Set<UUID> grantedResourceIds,
            List<CourseResourceSummary> grantedResources,
            List<StudyAssistantGrant> grants
    ) {
    }
}
