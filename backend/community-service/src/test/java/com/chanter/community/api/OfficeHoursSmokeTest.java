package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    void scheduleUsesAuthenticatedInstructorIdentity() throws Exception {
        UUID ownerUserId = UUID.randomUUID();

        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        Instant startsAt = Instant.now().plus(30, ChronoUnit.MINUTES);
        Instant endsAt = startsAt.plus(1, ChronoUnit.HOURS);

        MvcResult scheduleResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/office-hours", course.cohort().id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "startsAt", startsAt.toString(),
                                "endsAt", endsAt.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        OfficeHoursSessionResponse session = objectMapper.readValue(
                scheduleResult.getResponse().getContentAsString(),
                OfficeHoursSessionResponse.class
        );
        assertThat(session.scheduledByUserId()).isEqualTo(ownerUserId);
    }

    @Test
    void instructorEditsScheduledSessionAndLearnerCannot() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        enrollLearner(course.cohort().id(), ownerUserId, learnerUserId);
        OfficeHoursSessionResponse session = scheduleSession(
                course.cohort().id(),
                ownerUserId,
                Instant.now().plus(2, ChronoUnit.HOURS),
                Instant.now().plus(3, ChronoUnit.HOURS)
        );
        Instant updatedStartsAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant updatedEndsAt = updatedStartsAt.plus(90, ChronoUnit.MINUTES);
        String updateBody = objectMapper.writeValueAsString(Map.of(
                "startsAt", updatedStartsAt.toString(),
                "endsAt", updatedEndsAt.toString()
        ));

        mockMvc.perform(patch("/api/v1/office-hours/{sessionId}", session.id())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isForbidden());

        MvcResult updateResult = mockMvc.perform(patch("/api/v1/office-hours/{sessionId}", session.id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andReturn();
        OfficeHoursSessionResponse updated = objectMapper.readValue(
                updateResult.getResponse().getContentAsString(),
                OfficeHoursSessionResponse.class
        );

        assertThat(updated.startsAt()).isEqualTo(updatedStartsAt);
        assertThat(updated.endsAt()).isEqualTo(updatedEndsAt);
    }

    @Test
    void instructorStartsScheduledSessionEarlyAndLearnerCannot() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        enrollLearner(course.cohort().id(), ownerUserId, learnerUserId);
        OfficeHoursSessionResponse session = scheduleSession(
                course.cohort().id(),
                ownerUserId,
                Instant.now().plus(2, ChronoUnit.HOURS),
                Instant.now().plus(3, ChronoUnit.HOURS)
        );

        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/start", session.id())
                        .with(asUser(learnerUserId)))
                .andExpect(status().isForbidden());

        MvcResult startResult = mockMvc.perform(post("/api/v1/office-hours/{sessionId}/start", session.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        OfficeHoursSessionResponse started = objectMapper.readValue(
                startResult.getResponse().getContentAsString(),
                OfficeHoursSessionResponse.class
        );
        assertThat(started.status()).isEqualTo("LIVE");
    }

    @Test
    void lifecycleRejectsEndingScheduledAndStartingExpiredSessions() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        OfficeHoursSessionResponse futureSession = scheduleSession(
                course.cohort().id(),
                ownerUserId,
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(2, ChronoUnit.HOURS)
        );
        OfficeHoursSessionResponse expiredSession = scheduleSession(
                course.cohort().id(),
                ownerUserId,
                Instant.now().minus(2, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS)
        );

        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/end", futureSession.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/start", expiredSession.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isGone());
    }

    @Test
    void instructorCancelsScheduledSessionAndLearnerCannot() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        enrollLearner(course.cohort().id(), ownerUserId, learnerUserId);
        OfficeHoursSessionResponse session = scheduleSession(
                course.cohort().id(),
                ownerUserId,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(25, ChronoUnit.HOURS)
        );

        mockMvc.perform(delete("/api/v1/office-hours/{sessionId}", session.id())
                        .with(asUser(learnerUserId)))
                .andExpect(status().isForbidden());

        MvcResult cancelResult = mockMvc.perform(delete("/api/v1/office-hours/{sessionId}", session.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        OfficeHoursSessionResponse cancelled = objectMapper.readValue(
                cancelResult.getResponse().getContentAsString(),
                OfficeHoursSessionResponse.class
        );
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }

    @Test
    void endSessionCannotImpersonateInstructorThroughRequestBody() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        enrollLearner(course.cohort().id(), ownerUserId, learnerUserId);
        OfficeHoursSessionResponse session = scheduleSession(
                course.cohort().id(),
                ownerUserId,
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(2, ChronoUnit.HOURS)
        );
        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/start", session.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/participants", session.id())
                        .with(asUser(learnerUserId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/end", session.id())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actorUserId", ownerUserId.toString()
                        ))))
                .andExpect(status().isForbidden());

        MvcResult endResult = mockMvc.perform(post("/api/v1/office-hours/{sessionId}/end", session.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        OfficeHoursSessionResponse ended = objectMapper.readValue(
                endResult.getResponse().getContentAsString(),
                OfficeHoursSessionResponse.class
        );
        assertThat(ended.status()).isEqualTo("ENDED");

        MvcResult rosterResult = mockMvc.perform(get("/api/v1/office-hours/{sessionId}/participants", session.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        OfficeHoursParticipantListResponse roster = objectMapper.readValue(
                rosterResult.getResponse().getContentAsString(),
                OfficeHoursParticipantListResponse.class
        );
        assertThat(roster.participants()).isEmpty();
    }

    @Test
    void enrolledLearnerJoinsLiveSessionAsDurableListener() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        enrollLearner(course.cohort().id(), ownerUserId, learnerUserId);
        OfficeHoursSessionResponse session = scheduleSession(
                course.cohort().id(),
                ownerUserId,
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(2, ChronoUnit.HOURS)
        );
        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/start", session.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk());

        MvcResult joinResult = mockMvc.perform(post("/api/v1/office-hours/{sessionId}/participants", session.id())
                        .with(asUser(learnerUserId)))
                .andExpect(status().isCreated())
                .andReturn();
        OfficeHoursParticipantResponse participant = objectMapper.readValue(
                joinResult.getResponse().getContentAsString(),
                OfficeHoursParticipantResponse.class
        );
        assertThat(participant.userId()).isEqualTo(learnerUserId);
        assertThat(participant.canSpeak()).isFalse();
        assertThat(participant.handRaised()).isFalse();

        MvcResult rosterResult = mockMvc.perform(get("/api/v1/office-hours/{sessionId}/participants", session.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        OfficeHoursParticipantListResponse roster = objectMapper.readValue(
                rosterResult.getResponse().getContentAsString(),
                OfficeHoursParticipantListResponse.class
        );
        assertThat(roster.participants()).extracting(OfficeHoursParticipantResponse::userId)
                .containsExactly(learnerUserId);
    }

    @Test
    void learnerRaisesAndLowersHandDurably() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        enrollLearner(course.cohort().id(), ownerUserId, learnerUserId);
        OfficeHoursSessionResponse session = scheduleSession(
                course.cohort().id(),
                ownerUserId,
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(2, ChronoUnit.HOURS)
        );
        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/start", session.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/participants", session.id())
                        .with(asUser(learnerUserId)))
                .andExpect(status().isCreated());

        MvcResult raiseResult = mockMvc.perform(patch(
                                "/api/v1/office-hours/{sessionId}/participants/me/hand", session.id())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("raised", true))))
                .andExpect(status().isOk())
                .andReturn();
        OfficeHoursParticipantResponse raised = objectMapper.readValue(
                raiseResult.getResponse().getContentAsString(),
                OfficeHoursParticipantResponse.class
        );
        assertThat(raised.handRaised()).isTrue();

        MvcResult lowerResult = mockMvc.perform(patch(
                                "/api/v1/office-hours/{sessionId}/participants/me/hand", session.id())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("raised", false))))
                .andExpect(status().isOk())
                .andReturn();
        OfficeHoursParticipantResponse lowered = objectMapper.readValue(
                lowerResult.getResponse().getContentAsString(),
                OfficeHoursParticipantResponse.class
        );
        assertThat(lowered.handRaised()).isFalse();
    }

    @Test
    void participantControlRequestsRequireExplicitBooleanValues() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        enrollLearner(course.cohort().id(), ownerUserId, learnerUserId);
        OfficeHoursSessionResponse session = scheduleSession(
                course.cohort().id(),
                ownerUserId,
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(2, ChronoUnit.HOURS)
        );
        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/start", session.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/participants", session.id())
                        .with(asUser(learnerUserId)))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/office-hours/{sessionId}/participants/me/hand", session.id())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch(
                                "/api/v1/office-hours/{sessionId}/participants/{userId}/speaking",
                                session.id(), learnerUserId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void instructorGrantsSpeakingAndLiveKitTokenReflectsPermission() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        enrollLearner(course.cohort().id(), ownerUserId, learnerUserId);
        OfficeHoursSessionResponse session = scheduleSession(
                course.cohort().id(),
                ownerUserId,
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(2, ChronoUnit.HOURS)
        );
        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/start", session.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/participants", session.id())
                        .with(asUser(learnerUserId)))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/v1/office-hours/{sessionId}/participants/me/hand", session.id())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("raised", true))))
                .andExpect(status().isOk());
        String grantBody = objectMapper.writeValueAsString(Map.of("canSpeak", true));

        mockMvc.perform(patch(
                                "/api/v1/office-hours/{sessionId}/participants/{userId}/speaking",
                                session.id(), learnerUserId)
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody))
                .andExpect(status().isForbidden());

        MvcResult grantResult = mockMvc.perform(patch(
                                "/api/v1/office-hours/{sessionId}/participants/{userId}/speaking",
                                session.id(), learnerUserId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody))
                .andExpect(status().isOk())
                .andReturn();
        OfficeHoursParticipantResponse granted = objectMapper.readValue(
                grantResult.getResponse().getContentAsString(),
                OfficeHoursParticipantResponse.class
        );
        assertThat(granted.canSpeak()).isTrue();
        assertThat(granted.handRaised()).isFalse();

        MvcResult tokenResult = mockMvc.perform(post("/api/v1/office-hours/{sessionId}/media-token", session.id())
                        .with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        VoiceMediaTokenResponse mediaToken = objectMapper.readValue(
                tokenResult.getResponse().getContentAsString(),
                VoiceMediaTokenResponse.class
        );
        assertThat(mediaToken.canSpeak()).isTrue();
        assertThat(mediaToken.canListen()).isTrue();
    }

    @Test
    void learnerLeavesLiveSessionAndDisappearsFromRoster() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        enrollLearner(course.cohort().id(), ownerUserId, learnerUserId);
        OfficeHoursSessionResponse session = scheduleSession(
                course.cohort().id(),
                ownerUserId,
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(2, ChronoUnit.HOURS)
        );
        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/start", session.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/participants", session.id())
                        .with(asUser(learnerUserId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/office-hours/{sessionId}/participants/me", session.id())
                        .with(asUser(learnerUserId)))
                .andExpect(status().isNoContent());

        MvcResult rosterResult = mockMvc.perform(get("/api/v1/office-hours/{sessionId}/participants", session.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        OfficeHoursParticipantListResponse roster = objectMapper.readValue(
                rosterResult.getResponse().getContentAsString(),
                OfficeHoursParticipantListResponse.class
        );
        assertThat(roster.participants()).isEmpty();

        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/media-token", session.id())
                        .with(asUser(learnerUserId)))
                .andExpect(status().isConflict());
    }

    @Test
    void instructorSchedulesOfficeHoursAndLearnerJoinsDuringWindow() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();

        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        enrollLearner(course.cohort().id(), ownerUserId, learnerUserId);

        Instant startsAt = Instant.now().minus(5, ChronoUnit.MINUTES);
        Instant endsAt = Instant.now().plus(1, ChronoUnit.HOURS);

        MvcResult scheduleResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/office-hours", course.cohort().id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", ownerUserId.toString(),
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
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actorUserId", ownerUserId.toString()
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/office-hours/{sessionId}/voice-join", session.id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actorUserId", ownerUserId.toString()
                        ))))
                .andExpect(status().isOk());

        MvcResult listResult = mockMvc.perform(get("/api/v1/office-hours/{sessionId}/waitlist", session.id())
                        .with(asUser(ownerUserId))
                        .param("viewerUserId", ownerUserId.toString()))
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
        UUID learnerUserId = UUID.randomUUID();

        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        enrollLearner(course.cohort().id(), ownerUserId, learnerUserId);

        Instant startsAt = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant endsAt = Instant.now().plus(3, ChronoUnit.HOURS);

        MvcResult scheduleResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/office-hours", course.cohort().id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", ownerUserId.toString(),
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
        UUID learnerUserId = UUID.randomUUID();
        UUID strangerUserId = UUID.randomUUID();

        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        enrollLearner(course.cohort().id(), ownerUserId, learnerUserId);

        Instant startsAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        Instant endsAt = Instant.now().plus(1, ChronoUnit.HOURS);

        MvcResult scheduleResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/office-hours", course.cohort().id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", ownerUserId.toString(),
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
        UUID learnerUserId = UUID.randomUUID();

        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        enrollLearner(course.cohort().id(), ownerUserId, learnerUserId);

        Instant startsAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        Instant endsAt = Instant.now().plus(1, ChronoUnit.HOURS);

        MvcResult scheduleResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/office-hours", course.cohort().id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", ownerUserId.toString(),
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
        UUID learnerUserId = UUID.randomUUID();

        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServer.id(), ownerUserId);
        enrollLearner(course.cohort().id(), ownerUserId, learnerUserId);

        Instant startsAt = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant endsAt = Instant.now().minus(1, ChronoUnit.MINUTES);

        MvcResult scheduleResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/office-hours", course.cohort().id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", ownerUserId.toString(),
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

    private CourseResponse createCourse(UUID studyServerId, UUID ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/courses", studyServerId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Office Hours Course",
                                "cohortName", "Fall 2026"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), CourseResponse.class);
    }

    private void enrollLearner(UUID cohortId, UUID ownerUserId, UUID learnerUserId) throws Exception {
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", cohortId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isCreated());
    }

    private OfficeHoursSessionResponse scheduleSession(
            UUID cohortId,
            UUID instructorUserId,
            Instant startsAt,
            Instant endsAt
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/office-hours", cohortId)
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "startsAt", startsAt.toString(),
                                "endsAt", endsAt.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), OfficeHoursSessionResponse.class);
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
            UUID scheduledByUserId,
            Instant startsAt,
            Instant endsAt,
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

    private record OfficeHoursParticipantResponse(
            UUID sessionId,
            UUID userId,
            boolean canSpeak,
            boolean handRaised,
            Instant joinedAt
    ) {
    }

    private record OfficeHoursParticipantListResponse(List<OfficeHoursParticipantResponse> participants) {
    }

    private record VoiceMediaTokenResponse(boolean canSpeak, boolean canListen) {
    }
}
