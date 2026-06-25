package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class OfficeHoursSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void instructorSchedulesOfficeHoursAndLearnerJoinsDuringWindow() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();

        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId, instructorUserId);
        enrollLearner(course.cohort().id(), instructorUserId, learnerUserId);

        Instant startsAt = Instant.now().minus(5, ChronoUnit.MINUTES);
        Instant endsAt = Instant.now().plus(1, ChronoUnit.HOURS);

        MvcResult scheduleResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/office-hours", course.cohort().id())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", instructorUserId.toString(),
                                "startsAt", startsAt.toString(),
                                "endsAt", endsAt.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        OfficeHoursSessionResponse session = objectMapper.readValue(
                scheduleResult.getResponse().getContentAsString(),
                OfficeHoursSessionResponse.class
        );

        assertThat(session.status()).isEqualTo("SCHEDULED");
        assertThat(session.cohortId()).isEqualTo(course.cohort().id());

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/office-hours", course.cohort().id())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", learnerUserId.toString(),
                                "startsAt", startsAt.toString(),
                                "endsAt", endsAt.toString()
                        ))))
                .andExpect(status().isForbidden());

        MvcResult waitlistResult = mockMvc.perform(post("/api/v1/office-hours/{sessionId}/waitlist", session.id())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        OfficeHoursWaitlistEntryResponse waitlistEntry = objectMapper.readValue(
                waitlistResult.getResponse().getContentAsString(),
                OfficeHoursWaitlistEntryResponse.class
        );
        assertThat(waitlistEntry.status()).isEqualTo("WAITING");

        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/admit-next", session.id())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actorUserId", instructorUserId.toString()
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/voice-join", session.id())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actorUserId", instructorUserId.toString()
                        ))))
                .andExpect(status().isOk());

        MvcResult listResult = mockMvc.perform(get("/api/v1/office-hours/{sessionId}/waitlist", session.id())
                        .with(asUser(instructorUserId))
                        .param("viewerUserId", instructorUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        OfficeHoursWaitlistListResponse waitlist = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                OfficeHoursWaitlistListResponse.class
        );
        assertThat(waitlist.waitlistEntries()).extracting(OfficeHoursWaitlistEntryResponse::status)
                .containsExactly("ADMITTED");
    }

    @Test
    void learnerCannotJoinOutsideOfficeHoursWindow() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();

        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId, instructorUserId);
        enrollLearner(course.cohort().id(), instructorUserId, learnerUserId);

        Instant startsAt = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant endsAt = Instant.now().plus(3, ChronoUnit.HOURS);

        MvcResult scheduleResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/office-hours", course.cohort().id())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", instructorUserId.toString(),
                                "startsAt", startsAt.toString(),
                                "endsAt", endsAt.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        OfficeHoursSessionResponse session = objectMapper.readValue(
                scheduleResult.getResponse().getContentAsString(),
                OfficeHoursSessionResponse.class
        );

        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/waitlist", session.id())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonEnrolledLearnerCannotJoinOfficeHoursWaitlist() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID strangerUserId = UUID.randomUUID();

        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId, instructorUserId);
        enrollLearner(course.cohort().id(), instructorUserId, learnerUserId);

        Instant startsAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        Instant endsAt = Instant.now().plus(1, ChronoUnit.HOURS);

        MvcResult scheduleResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/office-hours", course.cohort().id())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", instructorUserId.toString(),
                                "startsAt", startsAt.toString(),
                                "endsAt", endsAt.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        OfficeHoursSessionResponse session = objectMapper.readValue(
                scheduleResult.getResponse().getContentAsString(),
                OfficeHoursSessionResponse.class
        );

        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/waitlist", session.id())
                        .with(asUser(strangerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", strangerUserId.toString()
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void learnerCannotListOfficeHoursWaitlist() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();

        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId, instructorUserId);
        enrollLearner(course.cohort().id(), instructorUserId, learnerUserId);

        Instant startsAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        Instant endsAt = Instant.now().plus(1, ChronoUnit.HOURS);

        MvcResult scheduleResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/office-hours", course.cohort().id())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", instructorUserId.toString(),
                                "startsAt", startsAt.toString(),
                                "endsAt", endsAt.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        OfficeHoursSessionResponse session = objectMapper.readValue(
                scheduleResult.getResponse().getContentAsString(),
                OfficeHoursSessionResponse.class
        );

        mockMvc.perform(get("/api/v1/office-hours/{sessionId}/waitlist", session.id())
                        .with(asUser(learnerUserId))
                        .param("viewerUserId", learnerUserId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void learnerCannotJoinAfterOfficeHoursWindowEnds() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();

        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId, instructorUserId);
        enrollLearner(course.cohort().id(), instructorUserId, learnerUserId);

        Instant startsAt = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant endsAt = Instant.now().minus(1, ChronoUnit.MINUTES);

        MvcResult scheduleResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/office-hours", course.cohort().id())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", instructorUserId.toString(),
                                "startsAt", startsAt.toString(),
                                "endsAt", endsAt.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        OfficeHoursSessionResponse session = objectMapper.readValue(
                scheduleResult.getResponse().getContentAsString(),
                OfficeHoursSessionResponse.class
        );

        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/waitlist", session.id())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isForbidden());
    }

    private StudyServerResponse createStudyServer(UUID ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Office Hours Study Group"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), StudyServerResponse.class);
    }

    private CourseResponse createCourse(UUID studyServerId, UUID ownerUserId, UUID instructorUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/courses", studyServerId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Office Hours Course",
                                "instructorUserId", instructorUserId.toString(),
                                "cohortName", "Fall 2026"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), CourseResponse.class);
    }

    private void enrollLearner(UUID cohortId, UUID instructorUserId, UUID learnerUserId) throws Exception {
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", cohortId)
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isCreated());
    }

    private record StudyServerResponse(UUID id) {
    }

    private record CourseResponse(
            UUID id,
            CohortResponse cohort
    ) {
    }

    private record CohortResponse(UUID id, String name) {
    }

    private record OfficeHoursSessionResponse(
            UUID id,
            UUID cohortId,
            String status
    ) {
    }

    private record OfficeHoursWaitlistEntryResponse(
            UUID sessionId,
            UUID learnerUserId,
            String status
    ) {
    }

    private record OfficeHoursWaitlistListResponse(List<OfficeHoursWaitlistEntryResponse> waitlistEntries) {
    }
}
