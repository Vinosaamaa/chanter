package com.chanter.community.application;

import com.chanter.community.domain.AccessibleStudyServer;
import com.chanter.community.domain.CohortCapabilities;
import com.chanter.community.domain.CourseCapabilities;
import com.chanter.community.domain.GrantCandidateCourse;
import com.chanter.community.domain.StudyAssistantGrantCandidates;
import com.chanter.community.domain.StudyAssistantViewerScope;
import com.chanter.community.domain.StudyServer;
import com.chanter.community.domain.StudyServerCapabilities;
import com.chanter.community.domain.StudyServerChannel;
import com.chanter.community.domain.StudyServerNavigation;
import com.chanter.community.domain.StudyServerNavigationCohort;
import com.chanter.community.domain.StudyServerNavigationCourse;
import com.chanter.common.auth.ModerationAccess;
import com.chanter.common.auth.ModerationAccess.Target;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudyServerNavigationService {

    private final CourseRepository courseRepository;
    private final StudyServerRepository studyServerRepository;
    private final ModerationAccess moderation;
    private final com.chanter.common.lifecycle.SourceDeletionRequests deletions;

    public StudyServerNavigationService(
            CourseRepository courseRepository,
            StudyServerRepository studyServerRepository,
            ModerationAccess moderation,
            com.chanter.common.lifecycle.SourceDeletionRequests deletions
    ) {
        this.courseRepository = courseRepository;
        this.studyServerRepository = studyServerRepository;
        this.moderation = moderation;
        this.deletions=deletions;
    }

    public List<AccessibleStudyServer> listAccessibleStudyServers(UUID userId) {
        var servers=courseRepository.listAccessibleStudyServers(userId);
        var visible=new java.util.ArrayList<AccessibleStudyServer>();
        for(int start=0;start<servers.size();start+=100) {
            var page=servers.subList(start,Math.min(start+100,servers.size()));
            var allowed=moderation.allowedSources(userId,page.stream().map(server -> new Target("STUDY_SERVER",server.id())).toList());
            page.stream().filter(server -> allowed.contains(new Target("STUDY_SERVER",server.id())) && !deletions.pending(server.id())).forEach(visible::add);
        }
        return List.copyOf(visible);
    }

    public StudyServerNavigation findNavigation(UUID studyServerId, UUID userId) {
        deletions.requireOpen(studyServerId);
        StudyServer studyServer = studyServerRepository.findById(studyServerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));
        if (!studyServerRepository.isStudyServerMember(studyServerId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Study Server navigation requires membership"
            );
        }

        moderation.requireAllowed(userId,List.of(new Target("STUDY_SERVER",studyServerId)));
        StudyAssistantViewerScope viewerScope = courseRepository.findViewerScope(studyServerId, userId)
                .orElseGet(() -> new StudyAssistantViewerScope(
                        studyServerId,
                        false,
                        List.of(),
                        List.of(),
                        List.of()
                ));
        StudyAssistantGrantCandidates candidates = courseRepository.findGrantCandidates(studyServerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));

        boolean owner = courseRepository.isStudyServerOwner(studyServerId, userId);
        boolean canTeach = owner || courseRepository.isInstructorOnAnyCourseInStudyServer(studyServerId, userId);
        StudyServerCapabilities capabilities = new StudyServerCapabilities(
                owner,
                canTeach,
                owner,
                owner,
                owner,
                owner
        );

        Set<UUID> instructedCourseIds = Set.copyOf(
                courseRepository.findInstructedCourseIds(studyServerId, userId)
        );
        Set<UUID> teachingAssistantCohortIds = Set.copyOf(
                courseRepository.findTeachingAssistantCohortIds(studyServerId, userId)
        );
        Set<UUID> enrolledCourseIds = Set.copyOf(viewerScope.enrolledCourseIds());
        Set<UUID> enrolledCohortIds = Set.copyOf(viewerScope.enrolledCohortIds());
        Set<UUID> accessibleCourseChannelIds = Set.copyOf(viewerScope.accessibleCourseChannelIds());
        List<StudyServerNavigationCourse> courses = candidates.courses().stream()
                .filter(course -> owner
                        || instructedCourseIds.contains(course.id())
                        || enrolledCourseIds.contains(course.id())
                        || course.cohorts().stream()
                                .anyMatch(cohort -> teachingAssistantCohortIds.contains(cohort.id())))
                .map(course -> toNavigationCourse(
                        course,
                        owner,
                        instructedCourseIds.contains(course.id()),
                        enrolledCourseIds.contains(course.id()),
                        enrolledCohortIds,
                        teachingAssistantCohortIds,
                        accessibleCourseChannelIds
                ))
                .toList();

        return new StudyServerNavigation(
                studyServer.id(),
                studyServer.name(),
                owner,
                capabilities,
                candidates.studyServerChannels(),
                courses
        );
    }

    private static StudyServerNavigationCourse toNavigationCourse(
            GrantCandidateCourse course,
            boolean owner,
            boolean instructor,
            boolean enrolled,
            Set<UUID> enrolledCohortIds,
            Set<UUID> teachingAssistantCohortIds,
            Set<UUID> accessibleCourseChannelIds
    ) {
        boolean canManageCourse = owner || instructor;
        boolean teachingAssistant = course.cohorts().stream()
                .anyMatch(cohort -> teachingAssistantCohortIds.contains(cohort.id()));
        CourseCapabilities capabilities = new CourseCapabilities(
                instructor,
                teachingAssistant,
                enrolled,
                canManageCourse,
                canManageCourse || teachingAssistant,
                canManageCourse,
                canManageCourse || teachingAssistant,
                canManageCourse,
                canManageCourse,
                canManageCourse
        );
        List<StudyServerNavigationCohort> cohorts = course.cohorts().stream()
                .filter(cohort -> canManageCourse
                        || enrolledCohortIds.contains(cohort.id())
                        || teachingAssistantCohortIds.contains(cohort.id()))
                .map(cohort -> new StudyServerNavigationCohort(
                        cohort.id(),
                        cohort.name(),
                        new CohortCapabilities(
                                enrolledCohortIds.contains(cohort.id()),
                                teachingAssistantCohortIds.contains(cohort.id()),
                                canManageCourse || teachingAssistantCohortIds.contains(cohort.id())
                        )
                ))
                .toList();

        return new StudyServerNavigationCourse(
                course.id(),
                course.title(),
                capabilities,
                cohorts,
                course.channels().stream()
                        .filter(channel -> canManageCourse || accessibleCourseChannelIds.contains(channel.id()))
                        .toList()
        );
    }
}
