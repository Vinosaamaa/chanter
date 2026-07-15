package com.chanter.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "chanter.internal-service-token=test-internal-service-token-for-auth")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSessionSmokeTest {

    private static final String INTERNAL_SERVICE_TOKEN = "test-internal-service-token-for-auth";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginRefreshAndMeWork() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "owner@study.local",
                                "password", "password123",
                                "displayName", "Owner"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        AuthSessionResponse registered = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                AuthSessionResponse.class
        );
        assertThat(registered.accessToken()).isNotBlank();
        assertThat(registered.refreshToken()).isNotBlank();
        assertThat(registered.user().email()).isEqualTo("owner@study.local");

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(AuthHeaders.AUTHORIZATION, AuthHeaders.BEARER_PREFIX + registered.accessToken()))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "owner@study.local",
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        AuthSessionResponse loggedIn = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthSessionResponse.class
        );

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", loggedIn.refreshToken()
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        AuthSessionResponse refreshed = objectMapper.readValue(
                refreshResult.getResponse().getContentAsString(),
                AuthSessionResponse.class
        );
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotEqualTo(loggedIn.refreshToken());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", loggedIn.refreshToken()
                        ))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", refreshed.refreshToken()
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", refreshed.refreshToken()
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUsersCanResolvePublicDisplayNamesWithoutEmailDisclosure() throws Exception {
        AuthSessionResponse viewer = register(
                "profile-viewer@study.local",
                "Profile Viewer"
        );
        AuthSessionResponse peer = register(
                "profile-peer@study.local",
                "Profile Peer"
        );
        UUID missingUserId = UUID.randomUUID();
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "userIds", List.of(peer.user().id(), missingUserId)
        ));

        mockMvc.perform(post("/api/v1/auth/profiles/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());

        MvcResult lookupResult = mockMvc.perform(post("/api/v1/auth/profiles/query")
                        .header(
                                AuthHeaders.AUTHORIZATION,
                                AuthHeaders.BEARER_PREFIX + viewer.accessToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();
        String responseBody = lookupResult.getResponse().getContentAsString();

        assertThat(responseBody).contains(peer.user().id().toString(), "Profile Peer");
        assertThat(responseBody).doesNotContain("profile-peer@study.local", "email");
        assertThat(responseBody).doesNotContain(missingUserId.toString());
    }

    @Test
    void internalDirectoryRequiresServiceAuthentication() throws Exception {
        mockMvc.perform(get("/internal/v1/users/by-email")
                        .queryParam("email", "private@study.local"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/users/by-email")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "wrong-token")
                        .queryParam("email", "private@study.local"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/v1/users/profiles/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userIds", List.of(UUID.randomUUID())
                        ))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/v1/users/profiles/query")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userIds", List.of(UUID.randomUUID())
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalDirectoryResolvesCanonicalProfileByNormalizedEmail() throws Exception {
        AuthSessionResponse learner = register(
                "roster-learner@study.local",
                "Roster Learner"
        );

        mockMvc.perform(get("/internal/v1/users/by-email")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_SERVICE_TOKEN)
                        .queryParam("email", " ROSTER-LEARNER@study.local "))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.userId").value(learner.user().id().toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.email").value("roster-learner@study.local"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.displayName").value("Roster Learner"));
    }

    @Test
    void internalDirectoryBatchesCanonicalProfilesInRequestedOrder() throws Exception {
        AuthSessionResponse first = register("first-roster@study.local", "First Roster");
        AuthSessionResponse second = register("second-roster@study.local", "Second Roster");

        mockMvc.perform(post("/internal/v1/users/profiles/query")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userIds", List.of(second.user().id(), UUID.randomUUID(), first.user().id())
                        ))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.profiles.length()").value(2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.profiles[0].displayName").value("Second Roster"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.profiles[1].displayName").value("First Roster"));
    }

    private AuthSessionResponse register(String email, String displayName) throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "password123",
                                "displayName", displayName
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                AuthSessionResponse.class
        );
    }
}
