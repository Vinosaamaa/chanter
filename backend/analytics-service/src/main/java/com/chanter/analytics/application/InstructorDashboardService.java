package com.chanter.analytics.application;

import com.chanter.analytics.api.InstructorDashboardResponse;
import com.chanter.analytics.infra.HttpAgentServiceClient;
import com.chanter.analytics.infra.HttpCommunityServiceClient;
import com.chanter.analytics.infra.HttpCommunityServiceClient.CourseResponse;
import com.chanter.analytics.infra.HttpCommunityServiceClient.GrantCandidatesResponse;
import com.chanter.analytics.infra.HttpMessageServiceClient;
import com.chanter.analytics.infra.HttpMessageServiceClient.MessageMetricsRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

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
        var messageMetrics = messageServiceClient.fetchMessageMetrics(new MessageMetricsRequest(
                viewerUserId,
                questionChannelIds(scope),
                cohortIds(scope),
                courseIds(scope)
        ));
        var aiUsage = agentServiceClient.fetchAiUsageMetrics(studyServerId, viewerUserId);

        return new InstructorDashboardResponse(
                studyServerId,
                aiUsage.planTier(),
                messageMetrics.unansweredSupportQuestions(),
                messageMetrics.faqCandidateGroups(),
                messageMetrics.approvedFaqCount(),
                messageMetrics.openTaQueueItems(),
                communityMetrics.liveOfficeHoursSessions(),
                communityMetrics.scheduledOfficeHoursSessions(),
                communityMetrics.officeHoursWaitlistEntries(),
                aiUsage.totalInvocations(),
                aiUsage.aiInvocationLimit(),
                aiUsage.remainingInvocations(),
                aiUsage.quotaExhausted(),
                aiUsage.lowConfidenceHandoffs()
        );
    }

    private static List<UUID> questionChannelIds(GrantCandidatesResponse scope) {
        List<UUID> channelIds = new ArrayList<>();
        for (CourseResponse course : scope.courses()) {
            course.channels().stream()
                    .filter(channel -> "questions".equals(channel.name()))
                    .map(HttpCommunityServiceClient.ChannelResponse::id)
                    .forEach(channelIds::add);
        }
        return channelIds;
    }

    private static List<UUID> cohortIds(GrantCandidatesResponse scope) {
        return scope.courses().stream()
                .flatMap(course -> course.cohorts().stream())
                .map(HttpCommunityServiceClient.CohortResponse::id)
                .toList();
    }

    private static List<UUID> courseIds(GrantCandidatesResponse scope) {
        return scope.courses().stream().map(CourseResponse::id).toList();
    }
}
