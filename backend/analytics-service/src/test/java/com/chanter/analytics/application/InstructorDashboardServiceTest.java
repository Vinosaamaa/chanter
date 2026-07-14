package com.chanter.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chanter.analytics.infra.HttpAgentServiceClient;
import com.chanter.analytics.infra.HttpAgentServiceClient.AiUsageMetricsResponse;
import com.chanter.analytics.infra.HttpCommunityServiceClient;
import com.chanter.analytics.infra.HttpCommunityServiceClient.ChannelResponse;
import com.chanter.analytics.infra.HttpCommunityServiceClient.CohortResponse;
import com.chanter.analytics.infra.HttpCommunityServiceClient.CommunityMetricsResponse;
import com.chanter.analytics.infra.HttpCommunityServiceClient.CourseResponse;
import com.chanter.analytics.infra.HttpCommunityServiceClient.GrantCandidatesResponse;
import com.chanter.analytics.infra.HttpMessageServiceClient;
import com.chanter.analytics.infra.HttpMessageServiceClient.MessageMetricsResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class InstructorDashboardServiceTest {

    @Test
    void dashboardSkipsCoursesOutsideTheInstructorsScope() {
        UUID studyServerId = UUID.randomUUID();
        UUID viewerUserId = UUID.randomUUID();
        CourseResponse permittedCourse = course("Permitted course");
        CourseResponse otherCourse = course("Another instructor's course");
        HttpCommunityServiceClient communityClient = mock(HttpCommunityServiceClient.class);
        HttpMessageServiceClient messageClient = mock(HttpMessageServiceClient.class);
        HttpAgentServiceClient agentClient = mock(HttpAgentServiceClient.class);

        when(communityClient.fetchGrantCandidates(studyServerId, viewerUserId))
                .thenReturn(new GrantCandidatesResponse(
                        studyServerId,
                        List.of(),
                        List.of(permittedCourse, otherCourse)
                ));
        when(communityClient.fetchCommunityMetrics(studyServerId, viewerUserId))
                .thenReturn(new CommunityMetricsResponse(studyServerId, 1, 2, 3));
        when(agentClient.fetchAiUsageMetrics(studyServerId, viewerUserId))
                .thenReturn(new AiUsageMetricsResponse(studyServerId, "FREE", 4, 50, 46, false, 1));
        when(messageClient.fetchMessageMetrics(argThat(request ->
                request != null && request.courseIds().equals(List.of(permittedCourse.id()))
        ))).thenReturn(new MessageMetricsResponse(4, 2, 3, 1));
        when(messageClient.fetchMessageMetrics(argThat(request ->
                request != null
                        && request.courseIds().isEmpty()
                        && request.cohortIds().equals(List.of(permittedCourse.cohorts().get(0).id()))
        ))).thenReturn(new MessageMetricsResponse(0, 0, 0, 0));
        when(messageClient.fetchMessageMetrics(argThat(request ->
                request != null
                        && request.courseIds().isEmpty()
                        && request.cohortIds().equals(List.of(permittedCourse.cohorts().get(1).id()))
        ))).thenReturn(new MessageMetricsResponse(0, 2, 0, 0));
        when(messageClient.fetchMessageMetrics(argThat(request ->
                request != null && request.courseIds().equals(List.of(otherCourse.id()))
        ))).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        var dashboard = new InstructorDashboardService(
                communityClient,
                messageClient,
                agentClient
        ).buildDashboard(studyServerId, viewerUserId);

        assertThat(dashboard.courses()).extracting(course -> course.courseId())
                .containsExactly(permittedCourse.id());
        assertThat(dashboard.unansweredSupportQuestions()).isEqualTo(4);
        assertThat(dashboard.openTaQueueItems()).isEqualTo(2);
        assertThat(dashboard.approvedFaqCount()).isEqualTo(3);
        assertThat(dashboard.repeatedQuestionGroups()).isEqualTo(1);
        assertThat(dashboard.courses().getFirst().cohorts())
                .extracting(cohort -> cohort.openTaQueueItems())
                .containsExactly(0, 2);
    }

    private static CourseResponse course(String title) {
        UUID courseId = UUID.randomUUID();
        return new CourseResponse(
                courseId,
                title,
                List.of(
                        new CohortResponse(UUID.randomUUID(), "Spring 2026"),
                        new CohortResponse(UUID.randomUUID(), "Fall 2026")
                ),
                List.of(new ChannelResponse(UUID.randomUUID(), "questions", "TEXT"))
        );
    }
}
