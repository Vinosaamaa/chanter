package com.chanter.message.api;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.JwtTokenService;
import com.chanter.common.auth.ModerationAccess;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountSuspensionFilterTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService tokens;
    @MockitoBean ModerationAccess moderation;

    @Test void directServiceRequestCannotUseAnOtherwiseValidSuspendedToken() throws Exception {
        UUID user = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(moderation).requireAccount(user);
        mvc.perform(get("/api/v1/friendships").header("Authorization", "Bearer "+tokens.createAccessToken(user)))
                .andExpect(status().isForbidden());
    }

    @Test void authorityOutageCannotBeTreatedAsAnActiveAccount() throws Exception {
        UUID user = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(moderation).requireAccount(user);
        mvc.perform(get("/api/v1/friendships").header("Authorization", "Bearer "+tokens.createAccessToken(user)))
                .andExpect(status().isServiceUnavailable());
    }
}
