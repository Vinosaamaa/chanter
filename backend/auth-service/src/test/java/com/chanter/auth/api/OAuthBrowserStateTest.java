package com.chanter.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "chanter.oauth.google.client-id=test-google-client",
        "chanter.oauth.google.client-secret=test-google-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuthBrowserStateTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void oauthAuthorizationStateIsBoundToTheBrowserThatStartedIt() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/oauth/providers")).andExpect(status().isOk()).andReturn();
        Cookie stateCookie = result.getResponse().getCookie("chanter_oauth_state");
        assertThat(stateCookie).isNotNull();
        assertThat(stateCookie.isHttpOnly()).isTrue();
        assertThat(stateCookie.getSecure()).isTrue();
        String url = json.readTree(result.getResponse().getContentAsString()).get("providers").get(0).get("authorizationUrl").asText();
        assertThat(url).contains("state=" + stateCookie.getValue());
        String payload = json.writeValueAsString(Map.of("code", "unredeemed-code", "state", stateCookie.getValue()));
        // A valid server-issued state still cannot be moved into another browser's callback.
        mvc.perform(post("/api/v1/auth/oauth/google/callback").header("Origin", "http://localhost:5173")
                        .header("X-Chanter-CSRF", "1").contentType("application/json").content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/oauth/google/callback").header("Origin", "http://localhost:5173")
                        .header("X-Chanter-CSRF", "1").cookie(new Cookie("chanter_oauth_state", "another-browser"))
                        .contentType("application/json").content(payload))
                .andExpect(status().isForbidden());
    }
}
