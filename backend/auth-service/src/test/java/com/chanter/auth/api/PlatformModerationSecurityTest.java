package com.chanter.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.JwtTokenService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformModerationSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService tokens;

    @Test void anonymousAndForgedOperatorHeadersCannotReadCases() throws Exception {
        mvc.perform(get("/api/v1/platform-admin/reports")
                .header("X-User-Id", UUID.randomUUID())
                .header("X-Platform-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test void ordinarySignedUserCannotReadCases() throws Exception {
        mvc.perform(get("/api/v1/platform-admin/reports")
                .header("Authorization", "Bearer " + tokens.createAccessToken(UUID.randomUUID()))
                .header("X-Platform-Role", "ADMIN"))
                .andExpect(status().isForbidden());
    }
}
