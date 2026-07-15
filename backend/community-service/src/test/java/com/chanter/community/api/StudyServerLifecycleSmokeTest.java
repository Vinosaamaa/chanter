package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.community.infra.TestAuthUserDirectoryClient;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudyServerLifecycleSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestAuthUserDirectoryClient userDirectory;

    @BeforeEach
    void resetDirectory() {
        userDirectory.reset();
    }

    @Test
    void ownerSubmittedDescriptionTypeAndInvitesArePersistedAndReloadable() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID inviteeUserId = UUID.randomUUID();
        userDirectory.register(ownerUserId, "owner@spring.example", "Study Owner");
        userDirectory.register(inviteeUserId, "teammate@spring.example", "Teammate");

        MvcResult createdResult = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Java Spring Study Group",
                                "description", "A cohort-based Java community",
                                "serverType", "PROGRAM",
                                "inviteEmails", List.of("teammate@spring.example")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("A cohort-based Java community"))
                .andExpect(jsonPath("$.serverType").value("PROGRAM"))
                .andExpect(jsonPath("$.pendingInvitations.length()").value(1))
                .andExpect(jsonPath("$.pendingInvitations[0].email").value("teammate@spring.example"))
                .andReturn();

        String location = createdResult.getResponse().getHeader("Location");

        mockMvc.perform(get(location).with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("A cohort-based Java community"))
                .andExpect(jsonPath("$.serverType").value("PROGRAM"))
                .andExpect(jsonPath("$.pendingInvitations[0].email").value("teammate@spring.example"));
    }

    @Test
    void invitedMemberCanAcceptStudyServerInvitation() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID inviteeUserId = UUID.randomUUID();
        userDirectory.register(ownerUserId, "owner@spring.example", "Study Owner");
        userDirectory.register(inviteeUserId, "teammate@spring.example", "Teammate");

        MvcResult createdResult = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Shared Hub",
                                "inviteEmails", List.of("teammate@spring.example")
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        StudyServerLifecycleResponse created = objectMapper.readValue(
                createdResult.getResponse().getContentAsString(),
                StudyServerLifecycleResponse.class
        );
        UUID invitationId = created.pendingInvitations().getFirst().id();

        mockMvc.perform(get("/api/v1/study-server-invitations").with(asUser(inviteeUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studyServerId").value(created.id().toString()))
                .andExpect(jsonPath("$[0].studyServerName").value("Shared Hub"));

        mockMvc.perform(post(
                        "/api/v1/study-servers/{studyServerId}/invitations/{invitationId}/accept",
                        created.id(),
                        invitationId
                ).with(asUser(inviteeUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/study-servers").with(asUser(inviteeUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + created.id() + "')]").exists());

        mockMvc.perform(get("/api/v1/study-server-invitations").with(asUser(inviteeUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownInviteEmailDoesNotLeavePartialStudyServer() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        userDirectory.register(ownerUserId, "owner@spring.example", "Study Owner");

        mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Should Roll Back",
                                "inviteEmails", List.of("missing@spring.example")
                        ))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/study-servers").with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Should Roll Back')]").doesNotExist());
    }

    @Test
    void nullInviteEmailElementsAreRejected() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        userDirectory.register(ownerUserId, "owner@spring.example", "Study Owner");

        mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Null Invite Hub",
                                  "inviteEmails": [null]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonOwnerCannotReadPendingInvitationEmails() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID inviteeUserId = UUID.randomUUID();
        UUID outsiderUserId = UUID.randomUUID();
        userDirectory.register(ownerUserId, "owner@spring.example", "Study Owner");
        userDirectory.register(inviteeUserId, "teammate@spring.example", "Teammate");

        MvcResult createdResult = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Private Invites Hub",
                                "inviteEmails", List.of("teammate@spring.example")
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        StudyServerLifecycleResponse created = objectMapper.readValue(
                createdResult.getResponse().getContentAsString(),
                StudyServerLifecycleResponse.class
        );

        mockMvc.perform(get("/api/v1/study-servers/{id}", created.id()).with(asUser(outsiderUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingInvitations.length()").value(0));

        mockMvc.perform(get("/api/v1/study-servers/{id}", created.id()).with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingInvitations.length()").value(1))
                .andExpect(jsonPath("$.pendingInvitations[0].email").value("teammate@spring.example"));
    }

    private record StudyServerLifecycleResponse(
            UUID id,
            List<PendingInvitationResponse> pendingInvitations
    ) {
    }

    private record PendingInvitationResponse(UUID id, String email) {
    }
}
