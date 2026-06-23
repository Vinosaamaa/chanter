package com.chanter.community.application;

import com.chanter.community.domain.StudyAssistantGrantCandidates;
import com.chanter.community.domain.StudyAssistantViewerScope;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudyAssistantGrantService {

    private final CourseRepository courseRepository;

    public StudyAssistantGrantService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    public StudyAssistantGrantCandidates findGrantCandidates(UUID studyServerId, UUID userId) {
        requireGrantCandidateAccess(studyServerId, userId);

        return courseRepository.findGrantCandidates(studyServerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));
    }

    public StudyAssistantViewerScope findViewerScope(UUID studyServerId, UUID userId) {
        return courseRepository.findViewerScope(studyServerId, userId)
                .orElseThrow(() -> {
                    if (!courseRepository.studyServerExists(studyServerId)) {
                        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found");
                    }
                    return new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Study Assistant presence requires Study Server membership or enrollment"
                    );
                });
    }

    private void requireGrantCandidateAccess(UUID studyServerId, UUID userId) {
        if (!courseRepository.studyServerExists(studyServerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found");
        }

        if (!courseRepository.isStudyServerOwner(studyServerId, userId)
                && !courseRepository.isInstructorOnAnyCourseInStudyServer(studyServerId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Grant candidates require Study Server Owner or Course Instructor role"
            );
        }
    }
}
