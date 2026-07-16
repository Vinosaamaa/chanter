package com.chanter.message.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.message.infra.TestCohortTaQueueAccessClient;
import com.chanter.message.infra.TestCourseChannelAccessClient;
import com.chanter.message.infra.TestCourseResourceAccessClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InstructorDashboardMetricsSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestCourseChannelAccessClient channelAccessClient;

    @Autowired
    private TestCohortTaQueueAccessClient cohortAccessClient;

    @Autowired
    private TestCourseResourceAccessClient courseAccessClient;

    @BeforeEach
    void setUp() {
        channelAccessClient.clear();
        cohortAccessClient.clear();
        courseAccessClient.clear();
    }

    @Test
    void requestBodyCannotSpoofAnInstructorForDashboardMetrics() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        UUID studyServerId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID attackerUserId = UUID.randomUUID();

        channelAccessClient.grantInstructorView(channelId, instructorUserId, courseId, "questions");
        channelAccessClient.registerChannel(channelId);
        cohortAccessClient.grantInstructorManage(cohortId, instructorUserId, courseId, studyServerId);
        cohortAccessClient.registerCohort(cohortId, courseId, studyServerId);
        courseAccessClient.grantInstructorUpload(courseId, instructorUserId);
        courseAccessClient.registerCourse(courseId);

        mockMvc.perform(post("/api/v1/instructor-dashboard/message-metrics")
                        .header(AuthHeaders.USER_ID, attackerUserId.toString())
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "viewerUserId", instructorUserId.toString(),
                                "questionChannelIds", List.of(channelId),
                                "cohortIds", List.of(cohortId),
                                "courseIds", List.of(courseId)
                        ))))
                .andExpect(status().isForbidden());
    }
}
