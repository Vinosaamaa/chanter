package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.community.infra.TestAuthUserDirectoryClient;
import com.fasterxml.jackson.databind.JsonNode;
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
class CommunityAnnouncementSmokeTest {

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
    void durableAnnouncementsSupportPublishEditArchiveAndIdempotentLikes() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID outsiderUserId = UUID.randomUUID();
        userDirectory.register(ownerUserId, "owner@hub.example", "Hub Owner");
        UUID studyServerId = createStudyServer(ownerUserId);

        MvcResult createdResult = mockMvc.perform(post("/api/v1/study-servers/{id}/announcements", studyServerId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Hackathon signups are open!",
                                "body", "Team up across courses — details inside."
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.viewerLiked").value(false))
                .andExpect(jsonPath("$.authorDisplayName").value("Hub Owner"))
                .andReturn();

        JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsString());
        UUID announcementId = UUID.fromString(created.get("id").asText());

        mockMvc.perform(put(
                        "/api/v1/study-servers/{id}/announcements/{announcementId}/reactions",
                        studyServerId,
                        announcementId
                )
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("liked", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerLiked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(put(
                        "/api/v1/study-servers/{id}/announcements/{announcementId}/reactions",
                        studyServerId,
                        announcementId
                )
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("liked", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(get("/api/v1/study-servers/{id}/announcements", studyServerId)
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcements.length()").value(1))
                .andExpect(jsonPath("$.announcements[0].id").value(announcementId.toString()));

        mockMvc.perform(patch(
                        "/api/v1/study-servers/{id}/announcements/{announcementId}",
                        studyServerId,
                        announcementId
                )
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Hackathon signups close Friday",
                                "body", "Updated details for teams."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hackathon signups close Friday"));

        mockMvc.perform(post("/api/v1/study-servers/{id}/announcements", studyServerId)
                        .with(asUser(outsiderUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Nope",
                                "body", "Unauthorized"
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/study-servers/{id}/announcements", studyServerId)
                        .with(asUser(outsiderUserId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                        "/api/v1/study-servers/{id}/announcements/{announcementId}/archive",
                        studyServerId,
                        announcementId
                ).with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/v1/study-servers/{id}/announcements", studyServerId)
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcements.length()").value(0));

        mockMvc.perform(get("/api/v1/study-servers/{id}/announcements", studyServerId)
                        .param("status", "ARCHIVED")
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcements.length()").value(1));

        mockMvc.perform(put(
                        "/api/v1/study-servers/{id}/announcements/{announcementId}/reactions",
                        studyServerId,
                        announcementId
                )
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("liked", false))))
                .andExpect(status().isConflict());
    }

    @Test
    void membersAndPostCreateInvitesAreDurable() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID inviteeUserId = UUID.randomUUID();
        userDirectory.register(ownerUserId, "owner@hub.example", "Hub Owner");
        userDirectory.register(inviteeUserId, "teammate@hub.example", "Teammate Lee");

        UUID studyServerId = createStudyServer(ownerUserId);

        mockMvc.perform(get("/api/v1/study-servers/{id}/member-summary", studyServerId)
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.preview[0].displayName").value("Hub Owner"));

        mockMvc.perform(post("/api/v1/study-servers/{id}/invitations", studyServerId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "inviteEmails", List.of("teammate@hub.example")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("teammate@hub.example"));

        mockMvc.perform(post("/api/v1/study-servers/{id}/invitations", studyServerId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "inviteEmails", List.of("teammate@hub.example")
                        ))))
                .andExpect(status().isConflict());

        MvcResult invitations = mockMvc.perform(get("/api/v1/study-server-invitations")
                        .with(asUser(inviteeUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        UUID invitationId = UUID.fromString(
                objectMapper.readTree(invitations.getResponse().getContentAsString()).get(0).get("id").asText()
        );

        mockMvc.perform(post(
                        "/api/v1/study-servers/{id}/invitations/{invitationId}/accept",
                        studyServerId,
                        invitationId
                ).with(asUser(inviteeUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/study-servers/{id}/members", studyServerId)
                        .param("search", "Teammate")
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2))
                .andExpect(jsonPath("$.filteredTotal").value(1))
                .andExpect(jsonPath("$.members[0].displayName").value("Teammate Lee"))
                .andExpect(jsonPath("$.members[0].role").value("MEMBER"));

        mockMvc.perform(get("/api/v1/study-servers/{id}/members", studyServerId)
                        .param("filter", "STAFF")
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filteredTotal").value(1))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));

        mockMvc.perform(post("/api/v1/study-servers/{id}/invitations", studyServerId)
                        .with(asUser(inviteeUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "inviteEmails", List.of("someone@hub.example")
                        ))))
                .andExpect(status().isForbidden());
    }

    private UUID createStudyServer(UUID ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Announcements Hub"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
}
