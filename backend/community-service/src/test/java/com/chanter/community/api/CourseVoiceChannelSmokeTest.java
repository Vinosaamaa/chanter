package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseVoiceChannelSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void enrolledLearnerJoinsCourseVoiceWithLiveKitAndOutsiderIsForbidden() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID outsiderUserId = UUID.randomUUID();
        CourseResponse course = createCourse(instructorUserId);
        UUID voiceChannelId = course.channels().stream()
                .filter(channel -> channel.kind().equals("VOICE"))
                .findFirst()
                .orElseThrow()
                .id();
        enroll(course.cohort().id(), instructorUserId, learnerUserId);

        mockMvc.perform(post("/api/v1/course-channels/{channelId}/voice-presences", voiceChannelId)
                        .with(asUser(learnerUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.channelId").value(voiceChannelId.toString()))
                .andExpect(jsonPath("$.memberUserId").value(learnerUserId.toString()));

        mockMvc.perform(get("/api/v1/course-channels/{channelId}/voice-presences", voiceChannelId)
                        .with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presences[0].memberUserId").value(learnerUserId.toString()));

        MvcResult tokenResult = mockMvc.perform(post(
                        "/api/v1/course-channels/{channelId}/media-token",
                        voiceChannelId
                ).with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomName").value("voice-" + voiceChannelId))
                .andExpect(jsonPath("$.serverUrl").isNotEmpty())
                .andExpect(jsonPath("$.participantToken").isNotEmpty())
                .andReturn();
        assertThat(tokenResult.getResponse().getContentAsString()).contains("canSpeak", "canListen");

        mockMvc.perform(get("/api/v1/course-channels/{channelId}/voice-presences", voiceChannelId)
                        .with(asUser(outsiderUserId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/course-channels/{channelId}/voice-presences", voiceChannelId)
                        .with(asUser(learnerUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/course-channels/{channelId}/voice-presences", voiceChannelId)
                        .with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presences").isEmpty());
    }

    @Test
    void mediaTokenDoesNotPublishPresenceUntilClientConfirmsConnection() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        CourseResponse course = createCourse(instructorUserId);
        UUID voiceChannelId = voiceChannelId(course);

        mockMvc.perform(post("/api/v1/course-channels/{channelId}/media-token", voiceChannelId)
                        .with(asUser(instructorUserId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/course-channels/{channelId}/voice-presences", voiceChannelId)
                        .with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presences").isEmpty());
    }

    @Test
    void expiredPresenceIsNotListedAndRevokedMemberCanStillCleanUp() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        CourseResponse course = createCourse(instructorUserId);
        UUID voiceChannelId = voiceChannelId(course);
        enroll(course.cohort().id(), instructorUserId, learnerUserId);

        mockMvc.perform(post("/api/v1/course-channels/{channelId}/voice-presences", voiceChannelId)
                        .with(asUser(learnerUserId)))
                .andExpect(status().isCreated());
        jdbcClient.sql("""
                        UPDATE course_voice_channel_presences
                        SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1' MINUTE
                        WHERE channel_id = :channelId
                        AND member_user_id = :memberUserId
                        """)
                .param("channelId", voiceChannelId)
                .param("memberUserId", learnerUserId)
                .update();

        mockMvc.perform(get("/api/v1/course-channels/{channelId}/voice-presences", voiceChannelId)
                        .with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presences").isEmpty());

        mockMvc.perform(delete(
                        "/api/v1/cohorts/{cohortId}/enrollments/{learnerUserId}",
                        course.cohort().id(),
                        learnerUserId
                ).with(asUser(instructorUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/course-channels/{channelId}/voice-presences", voiceChannelId)
                        .with(asUser(learnerUserId)))
                .andExpect(status().isNoContent());
    }

    private static UUID voiceChannelId(CourseResponse course) {
        return course.channels().stream()
                .filter(channel -> channel.kind().equals("VOICE"))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private CourseResponse createCourse(UUID instructorUserId) throws Exception {
        MvcResult serverResult = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Voice Course Hub"))))
                .andExpect(status().isCreated())
                .andReturn();
        StudyServerResponse server = objectMapper.readValue(
                serverResult.getResponse().getContentAsString(),
                StudyServerResponse.class
        );
        MvcResult courseResult = mockMvc.perform(post(
                        "/api/v1/study-servers/{studyServerId}/courses",
                        server.id()
                )
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Voice Collaboration",
                                "cohortName", "Summer 2026"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(courseResult.getResponse().getContentAsString(), CourseResponse.class);
    }

    private void enroll(UUID cohortId, UUID instructorUserId, UUID learnerUserId) throws Exception {
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", cohortId)
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId",
                                learnerUserId.toString()
                        ))))
                .andExpect(status().isCreated());
    }
}
