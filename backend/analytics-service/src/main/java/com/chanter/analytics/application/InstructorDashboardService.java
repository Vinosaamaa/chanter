package com.chanter.analytics.application;

import com.chanter.analytics.api.InstructorDashboardResponse;
import com.chanter.analytics.api.InstructorDashboardResponse.TeachingCohortResponse;
import com.chanter.analytics.api.InstructorDashboardResponse.TeachingCourseResponse;
import com.chanter.analytics.infra.HttpAgentServiceClient;
import com.chanter.analytics.infra.HttpCommunityServiceClient;
import com.chanter.analytics.infra.HttpCommunityServiceClient.CourseResponse;
import com.chanter.analytics.infra.HttpCommunityServiceClient.GrantCandidatesResponse;
import com.chanter.analytics.infra.HttpMessageServiceClient;
import com.chanter.analytics.infra.HttpMessageServiceClient.MessageMetricsRequest;
import com.chanter.analytics.infra.HttpMessageServiceClient.MessageMetricsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InstructorDashboardService {

    private final HttpCommunityServiceClient communityServiceClient;
    private final HttpMessageServiceClient messageServiceClient;
    private final HttpAgentServiceClient agentServiceClient;

    public InstructorDashboardService(
            HttpCommunityServiceClient communityServiceClient,
            HttpMessageServiceClient messageServiceClient,
            HttpAgentServiceClient agentServiceClient
    ) {
        this.communityServiceClient = communityServiceClient;
        this.messageServiceClient = messageServiceClient;
        this.agentServiceClient = agentServiceClient;
    }

    public InstructorDashboardResponse buildDashboard(UUID studyServerId, UUID viewerUserId) {
        GrantCandidatesResponse scope = communityServiceClient.fetchGrantCandidates(studyServerId, viewerUserId);
        var communityMetrics = communityServiceClient.fetchCommunityMetrics(studyServerId, viewerUserId);
        var aiUsage = agentServiceClient.fetchAiUsageMetrics(studyServerId, viewerUserId);
        List<TeachingCourseResponse> courses = buildCourseSummaries(scope, viewerUserId);

        return new InstructorDashboardResponse(
                studyServerId,
                aiUsage.planTier(),
                courses.stream().mapToInt(TeachingCourseResponse::unansweredSupportQuestions).sum(),
                courses.stream().mapToInt(TeachingCourseResponse::repeatedQuestionGroups).sum(),
                courses.stream().mapToInt(TeachingCourseResponse::approvedFaqCount).sum(),
                courses.stream().mapToInt(TeachingCourseResponse::openTaQueueItems).sum(),
                communityMetrics.liveOfficeHoursSessions(),
                communityMetrics.scheduledOfficeHoursSessions(),
                communityMetrics.officeHoursWaitlistEntries(),
                aiUsage.totalInvocations(),
                aiUsage.aiInvocationLimit(),
                aiUsage.remainingInvocations(),
                aiUsage.quotaExhausted(),
                aiUsage.lowConfidenceHandoffs(),
                courses
        );
    }

    private List<TeachingCourseResponse> buildCourseSummaries(
            GrantCandidatesResponse scope,
            UUID viewerUserId
    ) {
        List<TeachingCourseResponse> courses = new ArrayList<>();
        for (CourseResponse course : scope.courses()) {
            try {
                courses.add(buildCourseSummary(course, viewerUserId));
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() != HttpStatus.FORBIDDEN) {
                    throw exception;
                }
            }
        }
        return List.copyOf(courses);
    }

    private TeachingCourseResponse buildCourseSummary(CourseResponse course, UUID viewerUserId) {
        UUID questionChannelId = course.channels().stream()
                .filter(channel -> "questions".equals(channel.name()))
                .map(HttpCommunityServiceClient.ChannelResponse::id)
                .findFirst()
                .orElse(null);
        MessageMetricsResponse metrics = messageServiceClient.fetchMessageMetrics(new MessageMetricsRequest(
                viewerUserId,
                questionChannelId == null ? List.of() : List.of(questionChannelId),
                course.cohorts().stream().map(HttpCommunityServiceClient.CohortResponse::id).toList(),
                List.of(course.id())
        ));

        return new TeachingCourseResponse(
                course.id(),
                course.title(),
                questionChannelId,
                course.cohorts().stream()
                        .map(cohort -> new TeachingCohortResponse(
                                cohort.id(),
                                cohort.name(),
                                messageServiceClient.fetchMessageMetrics(new MessageMetricsRequest(
                                        viewerUserId,
                                        List.of(),
                                        List.of(cohort.id()),
                                        List.of()
                                )).openTaQueueItems()
                        ))
                        .toList(),
                metrics.unansweredSupportQuestions(),
                metrics.faqCandidateGroups(),
                metrics.approvedFaqCount(),
                metrics.openTaQueueItems()
        );
    }

}
