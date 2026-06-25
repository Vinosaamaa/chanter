package com.chanter.community.application;

import com.chanter.community.domain.AccessibleStudyServer;
import com.chanter.community.domain.GrantCandidateCourse;
import com.chanter.community.domain.StudyAssistantGrantCandidates;
import com.chanter.community.domain.StudyAssistantViewerScope;
import com.chanter.community.domain.StudyServer;
import com.chanter.community.domain.StudyServerChannel;
import com.chanter.community.domain.StudyServerNavigation;
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
    private final StudyAssistantGrantService studyAssistantGrantService;

    public StudyServerNavigationService(
            CourseRepository courseRepository,
            StudyServerRepository studyServerRepository,
            StudyAssistantGrantService studyAssistantGrantService
    ) {
        this.courseRepository = courseRepository;
        this.studyServerRepository = studyServerRepository;
        this.studyAssistantGrantService = studyAssistantGrantService;
    }

    public List<AccessibleStudyServer> listAccessibleStudyServers(UUID userId) {
        return courseRepository.listAccessibleStudyServers(userId);
    }

    public StudyServerNavigation findNavigation(UUID studyServerId, UUID userId) {
        StudyAssistantViewerScope viewerScope = studyAssistantGrantService.findViewerScope(studyServerId, userId);
        StudyAssistantGrantCandidates candidates = courseRepository.findGrantCandidates(studyServerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));
        StudyServer studyServer = studyServerRepository.findById(studyServerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));

        List<StudyServerChannel> studyServerChannels;
        List<GrantCandidateCourse> courses;

        if (viewerScope.canViewAllGrants()) {
            studyServerChannels = candidates.studyServerChannels();
            courses = candidates.courses();
        } else {
            studyServerChannels = studyServerRepository.isStudyServerMember(studyServerId, userId)
                    ? candidates.studyServerChannels()
                    : List.of();
            Set<UUID> enrolledCourseIds = Set.copyOf(viewerScope.enrolledCourseIds());
            courses = candidates.courses().stream()
                    .filter(course -> enrolledCourseIds.contains(course.id()))
                    .toList();
        }

        return new StudyServerNavigation(
                studyServer.id(),
                studyServer.name(),
                studyServerChannels,
                courses
        );
    }
}
