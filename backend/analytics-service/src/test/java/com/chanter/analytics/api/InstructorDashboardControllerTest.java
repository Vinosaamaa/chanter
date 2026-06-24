package com.chanter.analytics.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.analytics.application.InstructorDashboardService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InstructorDashboardController.class)
class InstructorDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InstructorDashboardService instructorDashboardService;

    @Test
    void instructorCanLoadDashboardAggregates() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID viewerUserId = UUID.randomUUID();

        when(instructorDashboardService.buildDashboard(eq(studyServerId), eq(viewerUserId)))
                .thenReturn(new InstructorDashboardResponse(
                        studyServerId,
                        4,
                        2,
                        5,
                        1,
                        1,
                        2,
                        3,
                        10,
                        3
                ));

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}/instructor-dashboard", studyServerId)
                        .param("viewerUserId", viewerUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unansweredSupportQuestions").value(4))
                .andExpect(jsonPath("$.openTaQueueItems").value(1))
                .andExpect(jsonPath("$.aiInvocationCount").value(10));
    }
}
