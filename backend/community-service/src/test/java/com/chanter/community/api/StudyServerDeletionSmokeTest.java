package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudyServerDeletionSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ownerCanDeleteStudyServer() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId, "Bootcamp Hub");

        mockMvc.perform(delete("/api/v1/study-servers/{studyServerId}", studyServer.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}", studyServer.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isNotFound());

        MvcResult listResult = mockMvc.perform(get("/api/v1/study-servers").with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        List<AccessibleStudyServerResponse> servers = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, AccessibleStudyServerResponse.class)
        );
        assertThat(servers).isEmpty();
    }

    @Test
    void enrolledLearnerCannotDeleteStudyServer() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId, "Spring Boot Cohort");

        MvcResult courseResult = mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/courses", studyServer.id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Spring Boot Foundations",
                                "cohortName", "Summer 2026"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        CourseResponse course = objectMapper.readValue(
                courseResult.getResponse().getContentAsString(),
                CourseResponse.class
        );

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", course.cohort().id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/study-servers/{studyServerId}", studyServer.id())
                        .with(asUser(learnerUserId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}", studyServer.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk());
    }

    @Test
    void strangerCannotDeleteStudyServer() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID strangerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId, "Data Science 101");

        mockMvc.perform(delete("/api/v1/study-servers/{studyServerId}", studyServer.id())
                        .with(asUser(strangerUserId)))
                .andExpect(status().isForbidden());
    }

    private StudyServerResponse createStudyServer(UUID ownerUserId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), StudyServerResponse.class);
    }

    private record StudyServerResponse(UUID id) {
    }

    private record CourseResponse(CohortResponse cohort) {
    }

    private record CohortResponse(UUID id) {
    }
}
