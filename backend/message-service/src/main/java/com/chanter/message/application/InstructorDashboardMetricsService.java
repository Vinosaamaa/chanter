package com.chanter.message.application;

import com.chanter.message.api.InstructorDashboardMessageMetricsRequest;
import com.chanter.message.application.CourseChannelAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InstructorDashboardMetricsService {

    private final CourseChannelAccessClient courseChannelAccessClient;
    private final InstructorDashboardMetricsRepository metricsRepository;
    private final ApprovedFaqService approvedFaqService;

    public InstructorDashboardMetricsService(
            CourseChannelAccessClient courseChannelAccessClient,
            InstructorDashboardMetricsRepository metricsRepository,
            ApprovedFaqService approvedFaqService
    ) {
        this.courseChannelAccessClient = courseChannelAccessClient;
        this.metricsRepository = metricsRepository;
        this.approvedFaqService = approvedFaqService;
    }

    public InstructorDashboardMessageMetrics buildMetrics(InstructorDashboardMessageMetricsRequest request) {
        requireInstructorDashboardAccess(request.viewerUserId(), request.questionChannelIds());

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

    private void requireInstructorDashboardAccess(UUID viewerUserId, List<UUID> questionChannelIds) {
        boolean canView = questionChannelIds.stream().anyMatch(channelId -> {
            try {
                CourseChannelAccess access = courseChannelAccessClient.requireAccess(channelId, viewerUserId);
                return access.canViewUnansweredSupportQuestions();
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                    return false;
                }
                throw exception;
            }
        });

        if (!canView) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Instructor Dashboard message metrics require Course Instructor access"
            );
        }
    }

    private int countFaqCandidateGroups(UUID viewerUserId, List<UUID> questionChannelIds) {
        return questionChannelIds.stream()
                .mapToInt(channelId -> approvedFaqService.listFaqCandidates(channelId, viewerUserId).size())
                .sum();
    }
}
