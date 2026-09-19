package com.chanter.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"chanter.auth.rate-limit.max-requests=2", "chanter.auth.rate-limit.window=1h"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModerationAppealsControllerTest {
    @Autowired MockMvc mvc;

    @Test void unknownAccountsHaveAGenericResponseWithoutCredentials() throws Exception {
        mvc.perform(post("/api/v1/auth/moderation-appeals/request")
                        .header("Origin", "http://localhost:5173")
                        .header("X-Chanter-CSRF", "1")
                        .header("X-Forwarded-For", "192.0.2.1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID()+"@unknown.test")))
                .andExpect(status().isAccepted()).andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test void accountAndIpHaveIndependentAttemptLimits() throws Exception {
        String account=UUID.randomUUID()+"@unknown.test";
        for (int attempt=1; attempt<=3; attempt++) {
            mvc.perform(post("/api/v1/auth/moderation-appeals/request")
                            .header("Origin", "http://localhost:5173")
                            .header("X-Chanter-CSRF", "1")
                            .header("X-Forwarded-For", "198.51.100."+attempt)
                            .contentType(MediaType.APPLICATION_JSON).content(body(account)))
                    .andExpect(attempt<=2 ? status().isAccepted() : status().isTooManyRequests());
        }
        for (int attempt=1; attempt<=3; attempt++) {
            mvc.perform(post("/api/v1/auth/moderation-appeals/request")
                            .header("Origin", "http://localhost:5173")
                            .header("X-Chanter-CSRF", "1")
                            .header("X-Forwarded-For", "203.0.113.4")
                            .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID()+"@unknown.test")))
                    .andExpect(attempt<=2 ? status().isAccepted() : status().isTooManyRequests());
        }
    }

    private String body(String email) {
        return "{\"email\":\""+email+"\",\"restrictionId\":\""+UUID.randomUUID()+"\"}";
    }
}
