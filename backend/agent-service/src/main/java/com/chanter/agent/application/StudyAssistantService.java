package com.chanter.agent.application;

import com.chanter.agent.application.CourseResourceCatalogClient.CourseResourceSummary;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.CourseCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.GrantCandidates;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.ViewerScope;
import com.chanter.agent.domain.ConfirmedGrant;
import com.chanter.agent.domain.GrantType;
import com.chanter.agent.domain.StudyAssistantGrant;
import com.chanter.agent.domain.StudyAssistantInstall;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudyAssistantService {

    private final StudyAssistantRepository repository;
    private final StudyAssistantGrantCandidatesClient grantCandidatesClient;
    private final CourseResourceCatalogClient courseResourceCatalogClient;
    private final Clock clock;

    public StudyAssistantService(
            StudyAssistantRepository repository,
            StudyAssistantGrantCandidatesClient grantCandidatesClient,
            CourseResourceCatalogClient courseResourceCatalogClient,
            Clock clock
    ) {
        this.repository = repository;
        this.grantCandidatesClient = grantCandidatesClient;
        this.courseResourceCatalogClient = courseResourceCatalogClient;
        this.clock = clock;
    }

    public InstallPreview previewInstall(UUID studyServerId, UUID instructorUserId) {
        GrantCandidates candidates = grantCandidatesClient.requireGrantCandidates(studyServerId, instructorUserId);
        boolean alreadyInstalled = repository.findInstallByStudyServerId(studyServerId).isPresent();

        List<CourseResourceSummary> courseResources = new ArrayList<>();
        for (CourseCandidate course : candidates.courses()) {
            courseResources.addAll(
                    courseResourceCatalogClient.listAiApprovedCourseResources(course.id(), instructorUserId)
            );
        }

        return new InstallPreview(
                studyServerId,
                alreadyInstalled,
                candidates,
                courseResources
        );
    }

    public StudyAssistantInstall install(UUID studyServerId, UUID instructorUserId, List<ConfirmedGrant> confirmedGrants) {
        if (repository.findInstallByStudyServerId(studyServerId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "AI Study Assistant is already installed in this Study Server"
            );
        }

        InstallPreview preview = previewInstall(studyServerId, instructorUserId);
        Set<String> allowedGrantKeys = allowedGrantKeys(preview);

        for (ConfirmedGrant grant : confirmedGrants) {
            if (!allowedGrantKeys.contains(grantKey(grant.grantType(), grant.grantTargetId()))) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Confirmed grants must be a subset of install preview candidates"
                );
            }
        }

        StudyAssistantInstall install = new StudyAssistantInstall(
                UUID.randomUUID(),
                studyServerId,
                instructorUserId,
                clock.instant()
        );

        return repository.saveInstall(install, confirmedGrants);
    }

    public Presence findPresence(UUID studyServerId, UUID viewerUserId) {
        ViewerScope viewerScope = grantCandidatesClient.requireViewerScope(studyServerId, viewerUserId);

        return repository.findInstallByStudyServerId(studyServerId)
                .map(install -> {
                    List<StudyAssistantGrant> grants = repository.findGrantsByInstallId(install.id());
                    Map<UUID, UUID> resourceCourseIds = resourceCourseIdsForGrants(
                            grants,
                            viewerScope,
                            viewerUserId
                    );
                    List<StudyAssistantGrant> visibleGrants = filterVisibleGrants(
                            grants,
                            viewerScope,
                            resourceCourseIds
                    );
                    return new Presence(studyServerId, true, visibleGrants);
                })
                .orElseGet(() -> new Presence(studyServerId, false, List.of()));
    }

    private Set<String> allowedGrantKeys(InstallPreview preview) {
        Set<String> keys = new HashSet<>();

        for (var channel : preview.candidates().studyServerChannels()) {
            keys.add(grantKey(GrantType.STUDY_SERVER_CHANNEL, channel.id()));
        }

        for (CourseCandidate course : preview.candidates().courses()) {
            keys.add(grantKey(GrantType.COURSE, course.id()));
            for (var cohort : course.cohorts()) {
                keys.add(grantKey(GrantType.COHORT, cohort.id()));
            }
            for (var channel : course.channels()) {
                keys.add(grantKey(GrantType.COURSE_CHANNEL, channel.id()));
            }
        }

        for (CourseResourceSummary resource : preview.courseResources()) {
            keys.add(grantKey(GrantType.COURSE_RESOURCE, resource.id()));
        }

        return keys;
    }

    private List<StudyAssistantGrant> filterVisibleGrants(
            List<StudyAssistantGrant> grants,
            ViewerScope viewerScope,
            Map<UUID, UUID> resourceCourseIds
    ) {
        if (viewerScope.canViewAllGrants()) {
            return grants;
        }

        boolean enrolledInStudyServer = !viewerScope.enrolledCourseIds().isEmpty();

        return grants.stream()
                .filter(grant -> switch (grant.grantType()) {
                    case STUDY_SERVER_CHANNEL -> enrolledInStudyServer;
                    case COURSE -> viewerScope.enrolledCourseIds().contains(grant.grantTargetId());
                    case COHORT -> viewerScope.enrolledCohortIds().contains(grant.grantTargetId());
                    case COURSE_CHANNEL -> viewerScope.accessibleCourseChannelIds().contains(grant.grantTargetId());
                    case COURSE_RESOURCE -> {
                        UUID courseId = resourceCourseIds.get(grant.grantTargetId());
                        yield courseId != null && viewerScope.enrolledCourseIds().contains(courseId);
                    }
                })
                .toList();
    }

    private Map<UUID, UUID> resourceCourseIdsForGrants(
            List<StudyAssistantGrant> grants,
            ViewerScope viewerScope,
            UUID viewerUserId
    ) {
        Set<UUID> resourceGrantIds = grants.stream()
                .filter(grant -> grant.grantType() == GrantType.COURSE_RESOURCE)
                .map(StudyAssistantGrant::grantTargetId)
                .collect(java.util.stream.Collectors.toSet());

        if (resourceGrantIds.isEmpty()) {
            return Map.of();
        }

        Set<UUID> courseIds = new HashSet<>();
        if (viewerScope.canViewAllGrants()) {
            grants.stream()
                    .filter(grant -> grant.grantType() == GrantType.COURSE)
                    .map(StudyAssistantGrant::grantTargetId)
                    .forEach(courseIds::add);
        } else {
            courseIds.addAll(viewerScope.enrolledCourseIds());
        }

        Map<UUID, UUID> resourceCourseIds = new HashMap<>();
        for (UUID courseId : courseIds) {
            for (CourseResourceSummary resource : courseResourceCatalogClient.listAiApprovedCourseResources(
                    courseId,
                    viewerUserId
            )) {
                if (resourceGrantIds.contains(resource.id())) {
                    resourceCourseIds.put(resource.id(), resource.courseId());
                }
            }
        }

        return resourceCourseIds;
    }

    private static String grantKey(GrantType grantType, UUID grantTargetId) {
        return grantType.name() + ":" + grantTargetId;
    }

    public record InstallPreview(
            UUID studyServerId,
            boolean alreadyInstalled,
            GrantCandidates candidates,
            List<CourseResourceSummary> courseResources
    ) {
    }

    public record Presence(
            UUID studyServerId,
            boolean installed,
            List<StudyAssistantGrant> grants
    ) {
    }
}
