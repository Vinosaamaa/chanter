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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.chanter.common.lifecycle.AccountDeletionParticipant terminal;
    @Autowired com.chanter.common.lifecycle.AccountDeletionProtocol protocol;

    @Test
    void ownerCanDeleteStudyServer() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId, "Bootcamp Hub");

        var accepted=mockMvc.perform(delete("/api/v1/study-servers/{studyServerId}", studyServer.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isAccepted()).andReturn().getResponse();
        UUID job=UUID.fromString(objectMapper.readTree(accepted.getContentAsString()).get("jobId").asText());
        var repeated=mockMvc.perform(delete("/api/v1/study-servers/{studyServerId}",studyServer.id()).with(asUser(ownerUserId)))
                .andExpect(status().isAccepted()).andReturn().getResponse();
        assertThat(objectMapper.readTree(repeated.getContentAsString()).get("jobId").asText()).isEqualTo(job.toString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_servers WHERE id=?",Integer.class,studyServer.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind='LIFECYCLE_DELETE_REQUEST' AND aggregate_key=?",Integer.class,
                com.chanter.common.lifecycle.AccountDeletionProtocol.key("STUDY_SERVER",studyServer.id()))).isEqualTo(1);

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}", studyServer.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isGone());

        MvcResult listResult = mockMvc.perform(get("/api/v1/study-servers").with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        List<AccessibleStudyServerResponse> servers = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, AccessibleStudyServerResponse.class)
        );
        assertThat(servers).isEmpty();
        long revision=jdbc.queryForObject("SELECT COALESCE(MAX(revision),0)+1 FROM lifecycle_terminal_targets",Long.class);
        UUID event=UUID.randomUUID(); var now=java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var entry=new com.chanter.common.lifecycle.TerminalJournal.Entry(revision,event,"STUDY_SERVER",studyServer.id(),"DELETE",now,
                com.chanter.common.lifecycle.TerminalJournal.RETENTION_POLICY,com.chanter.common.lifecycle.TerminalJournal.GENESIS,
                com.chanter.common.lifecycle.TerminalJournal.digest(revision,event,"STUDY_SERVER",studyServer.id(),now,com.chanter.common.lifecycle.TerminalJournal.GENESIS));
        var command=new com.chanter.common.events.DurableEvent(UUID.randomUUID(),1,"auth",revision,com.chanter.common.lifecycle.AccountDeletionProtocol.TERMINAL,
                com.chanter.common.lifecycle.AccountDeletionProtocol.key("STUDY_SERVER",studyServer.id()),protocol.encode(new com.chanter.common.lifecycle.AccountDeletionProtocol.Terminal(job,entry)));
        terminal.accept(command); terminal.accept(command);
        var afterTerminal=mockMvc.perform(delete("/api/v1/study-servers/{id}",studyServer.id()).with(asUser(ownerUserId)))
                .andExpect(status().isAccepted()).andReturn().getResponse();
        assertThat(objectMapper.readTree(afterTerminal.getContentAsString()).path("jobId").asText()).isEqualTo(job.toString());
        mockMvc.perform(delete("/api/v1/study-servers/{id}",studyServer.id()).with(asUser(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_servers WHERE id=?",Integer.class,studyServer.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_deleted_server_scope_digests WHERE study_server_id=?",Integer.class,studyServer.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind='DELETED_SCOPE_ADVANCE' AND aggregate_key LIKE ?",Integer.class,
                "DELETED_SCOPE:"+studyServer.id()+":%")).isEqualTo(2);
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
