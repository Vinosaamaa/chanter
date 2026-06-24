package com.chanter.community.application;

import com.chanter.community.domain.CommunityDashboardMetrics;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InstructorDashboardService {

    private final CourseRepository courseRepository;
    private final InstructorDashboardRepository instructorDashboardRepository;

    public InstructorDashboardService(
            CourseRepository courseRepository,
            InstructorDashboardRepository instructorDashboardRepository
    ) {
        this.courseRepository = courseRepository;
        this.instructorDashboardRepository = instructorDashboardRepository;
    }

    public CommunityDashboardMetrics findCommunityMetrics(UUID studyServerId, UUID userId) {
        requireDashboardAccess(studyServerId, userId);
        return instructorDashboardRepository.findCommunityMetrics(studyServerId);
    }

    private void requireDashboardAccess(UUID studyServerId, UUID userId) {
        if (!courseRepository.studyServerExists(studyServerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found");
        }

        if (!courseRepository.isStudyServerOwner(studyServerId, userId)
                && !courseRepository.isInstructorOnAnyCourseInStudyServer(studyServerId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Instructor Dashboard requires Study Server Owner or Course Instructor role"
            );
        }
    }
}
