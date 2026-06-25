package com.chanter.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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
class AuthSessionSmokeTest {

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
}
