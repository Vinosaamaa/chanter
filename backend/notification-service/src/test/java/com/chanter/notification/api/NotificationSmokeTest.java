package com.chanter.notification.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.notification.domain.NotificationKind;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class NotificationSmokeTest {

    private static final String INTERNAL_TOKEN = "test-internal-service-token-for-notification";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createListReadDoneAndUnreadCount() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();

        MvcResult createResult = mockMvc.perform(post("/api/v1/internal/notifications")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId,
                                "kind", NotificationKind.SUPPORT_QUESTION_ANSWERED.name(),
                                "title", "Your question was answered",
                                "bodyPreview", "Merge Sort is O(n log n).",
                                "courseLabel", "CS 101",
                                "href", "/app/inbox?channelId=" + channelId + "&questionId=" + sourceId,
                                "sourceType", "SUPPORT_QUESTION",
                                "sourceId", sourceId,
                                "courseId", courseId,
                                "channelId", channelId
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("SUPPORT_QUESTION_ANSWERED"))
                .andExpect(jsonPath("$.filterBucket").value("MENTIONS"))
                .andExpect(jsonPath("$.unread").value(true))
                .andReturn();

        NotificationResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                NotificationResponse.class
        );

        mockMvc.perform(post("/api/v1/internal/notifications")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId,
                                "kind", NotificationKind.SUPPORT_QUESTION_ANSWERED.name(),
                                "title", "Your question was answered (updated)",
                                "bodyPreview", "Updated preview",
                                "courseLabel", "CS 101",
                                "href", "/app/inbox?channelId=" + channelId + "&questionId=" + sourceId,
                                "sourceType", "SUPPORT_QUESTION",
                                "sourceId", sourceId,
                                "courseId", courseId,
                                "channelId", channelId
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(created.id().toString()))
                .andExpect(jsonPath("$.title").value("Your question was answered (updated)"));

        mockMvc.perform(get("/api/v1/me/notifications/unread-count")
                        .header(AuthHeaders.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(get("/api/v1/me/notifications")
                        .param("filter", "MENTIONS")
                        .param("status", "OPEN")
                        .header(AuthHeaders.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].id").value(created.id().toString()));

        mockMvc.perform(post("/api/v1/me/notifications/{id}/read", created.id())
                        .header(AuthHeaders.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(false));

        mockMvc.perform(get("/api/v1/me/notifications/unread-count")
                        .header(AuthHeaders.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));

        mockMvc.perform(post("/api/v1/me/notifications/{id}/done", created.id())
                        .header(AuthHeaders.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doneAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/me/notifications")
                        .param("status", "OPEN")
                        .header(AuthHeaders.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(0));

        mockMvc.perform(get("/api/v1/me/notifications")
                        .param("status", "DONE")
                        .header(AuthHeaders.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1));
    }

    @Test
    void internalCreateRequiresServiceToken() throws Exception {
        mockMvc.perform(post("/api/v1/internal/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", UUID.randomUUID(),
                                "kind", NotificationKind.ANNOUNCEMENT.name(),
                                "title", "Hello",
                                "href", "/app/inbox",
                                "sourceType", "ANNOUNCEMENT",
                                "sourceId", UUID.randomUUID()
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me/notifications"))
                .andExpect(status().isUnauthorized());
    }
}
