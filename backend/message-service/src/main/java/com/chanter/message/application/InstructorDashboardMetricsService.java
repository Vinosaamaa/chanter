package com.chanter.message.application;

import com.chanter.message.api.InstructorDashboardMessageMetricsRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InstructorDashboardMetricsService {

    private final CourseChannelAccessClient courseChannelAccessClient;
    private final CohortTaQueueAccessClient cohortTaQueueAccessClient;
    private final CourseResourceAccessClient courseResourceAccessClient;
    private final InstructorDashboardMetricsRepository metricsRepository;
    private final ApprovedFaqService approvedFaqService;

    public InstructorDashboardMetricsService(
            CourseChannelAccessClient courseChannelAccessClient,
            CohortTaQueueAccessClient cohortTaQueueAccessClient,
            CourseResourceAccessClient courseResourceAccessClient,
            InstructorDashboardMetricsRepository metricsRepository,
            ApprovedFaqService approvedFaqService
    ) {
        this.courseChannelAccessClient = courseChannelAccessClient;
        this.cohortTaQueueAccessClient = cohortTaQueueAccessClient;
        this.courseResourceAccessClient = courseResourceAccessClient;
        this.metricsRepository = metricsRepository;
        this.approvedFaqService = approvedFaqService;
    }

    public InstructorDashboardMessageMetrics buildMetrics(InstructorDashboardMessageMetricsRequest request) {
        requireInstructorDashboardAccess(request);

        int unansweredSupportQuestions = metricsRepository.countUnansweredSupportQuestions(request.questionChannelIds());
        int openTaQueueItems = metricsRepository.countOpenTaQueueItems(request.cohortIds());
        int approvedFaqCount = metricsRepository.countApprovedFaqs(request.courseIds());
        int faqCandidateGroups = countFaqCandidateGroups(request.viewerUserId(), request.questionChannelIds());

        return new InstructorDashboardMessageMetrics(
                unansweredSupportQuestions,
                openTaQueueItems,
                approvedFaqCount,
                faqCandidateGroups
        );
    }

    private void requireInstructorDashboardAccess(InstructorDashboardMessageMetricsRequest request) {
        UUID viewerUserId = request.viewerUserId();

        if (!request.questionChannelIds().stream().allMatch(channelId -> hasQuestionChannelAccess(channelId, viewerUserId))
                || !request.cohortIds().stream().allMatch(cohortId -> hasCohortAccess(cohortId, viewerUserId))
                || !request.courseIds().stream().allMatch(courseId -> hasCourseAccess(courseId, viewerUserId))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Instructor Dashboard message metrics require Course Instructor access"
            );
        }
    }

    private boolean hasQuestionChannelAccess(UUID channelId, UUID viewerUserId) {
        try {
            CourseChannelAccess access = courseChannelAccessClient.requireAccess(channelId, viewerUserId);
            return access.canViewUnansweredSupportQuestions();
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                return false;
            }
            throw exception;
        }
    }

    private boolean hasCohortAccess(UUID cohortId, UUID viewerUserId) {
        try {
            CohortTaQueueAccess access = cohortTaQueueAccessClient.requireAccess(cohortId, viewerUserId);
            return access.canManageTaQueue();
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                return false;
            }
            throw exception;
        }
    }

    private boolean hasCourseAccess(UUID courseId, UUID viewerUserId) {
        try {
            CourseResourceAccess access = courseResourceAccessClient.requireAccess(courseId, viewerUserId);
            return access.canUploadCourseResource();
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                return false;
            }
            throw exception;
        }
    }

    private int countFaqCandidateGroups(UUID viewerUserId, List<UUID> questionChannelIds) {
        return questionChannelIds.stream()
                .mapToInt(channelId -> approvedFaqService.listFaqCandidates(channelId, viewerUserId).size())
                .sum();
    }
}
