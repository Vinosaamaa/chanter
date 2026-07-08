package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class SocialMembershipSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void usersOnSameStudyServerAreCoMembers() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();

        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", course.cohort().id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isCreated());

        MvcResult coMembershipResult = mockMvc.perform(get(
                        "/api/v1/users/{peerUserId}/co-membership",
                        learnerUserId
                ).with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        CoMembershipResponse response = objectMapper.readValue(
                coMembershipResult.getResponse().getContentAsString(),
                CoMembershipResponse.class
        );

        assertThat(response.coMembers()).isTrue();
    }

    @Test
    void usersWithoutSharedStudyServerAreNotCoMembers() throws Exception {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        createStudyServer(firstUserId);
        createStudyServer(secondUserId);

        MvcResult coMembershipResult = mockMvc.perform(get(
                        "/api/v1/users/{peerUserId}/co-membership",
                        secondUserId
                ).with(asUser(firstUserId)))
                .andExpect(status().isOk())
                .andReturn();
        CoMembershipResponse response = objectMapper.readValue(
                coMembershipResult.getResponse().getContentAsString(),
                CoMembershipResponse.class
        );

        assertThat(response.coMembers()).isFalse();
    }

    private StudyServerResponse createStudyServer(UUID ownerUserId) throws Exception {
        MvcResult createdResult = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Shared Study Group"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(
                createdResult.getResponse().getContentAsString(),
                StudyServerResponse.class
        );
    }

    private CourseResponse createCourse(UUID studyServerId, UUID ownerUserId) throws Exception {
        MvcResult courseResult = mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/courses", studyServerId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Spring Boot Foundations",
                                "cohortName", "Summer 2026"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(
                courseResult.getResponse().getContentAsString(),
                CourseResponse.class
        );
    }
}
