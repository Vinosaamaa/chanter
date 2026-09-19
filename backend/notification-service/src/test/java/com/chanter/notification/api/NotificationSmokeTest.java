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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationSmokeTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.chanter.notification.application.NotificationVisibility visibility;

    @org.junit.jupiter.api.BeforeEach
    void allowSourceVisibility() {
        org.mockito.Mockito.when(visibility.canView(org.mockito.ArgumentMatchers.any())).thenReturn(true);
    }

    private static final String INTERNAL_TOKEN = "test-internal-service-token-for-notification";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.chanter.notification.application.NotificationService notificationService;

    @Test
    void hiddenRecentNotificationsDoNotHideOlderVisibleInboxItems() throws Exception {
        UUID user = UUID.randomUUID();
        UUID visibleSource = UUID.randomUUID();
        for (int index = 0; index < 102; index++) {
            notificationService.create(new com.chanter.notification.application.NotificationRepository.CreateCommand(
                    user, NotificationKind.ANNOUNCEMENT, null, "Announcement " + index, null, null, "/app/inbox",
                    "ANNOUNCEMENT", index == 0 ? visibleSource : UUID.randomUUID(), null, null, null, null));
        }
        org.mockito.Mockito.when(visibility.canView(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation ->
                ((com.chanter.notification.domain.Notification) invocation.getArgument(0)).sourceId().equals(visibleSource));
        mockMvc.perform(get("/api/v1/me/notifications").header(AuthHeaders.USER_ID, user)
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].sourceId").value(visibleSource.toString()));
        mockMvc.perform(get("/api/v1/me/notifications/unread-count").header(AuthHeaders.USER_ID, user)
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

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
                        .header(AuthHeaders.USER_ID, userId)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(get("/api/v1/me/notifications")
                        .param("filter", "MENTIONS")
                        .param("status", "OPEN")
                        .header(AuthHeaders.USER_ID, userId)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].id").value(created.id().toString()));

        mockMvc.perform(post("/api/v1/me/notifications/{id}/read", created.id())
                        .header(AuthHeaders.USER_ID, userId)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(false));

        mockMvc.perform(get("/api/v1/me/notifications/unread-count")
                        .header(AuthHeaders.USER_ID, userId)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));

        mockMvc.perform(post("/api/v1/me/notifications/{id}/done", created.id())
                        .header(AuthHeaders.USER_ID, userId)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doneAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/me/notifications")
                        .param("status", "OPEN")
                        .header(AuthHeaders.USER_ID, userId)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(0));

        mockMvc.perform(get("/api/v1/me/notifications")
                        .param("status", "DONE")
                        .header(AuthHeaders.USER_ID, userId)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
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
    void revokedSourceAccessHidesContentAndUnreadCount() throws Exception {
        UUID userId = UUID.randomUUID();
        var result = mockMvc.perform(post("/api/v1/internal/notifications")
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("userId", userId, "kind", "ANNOUNCEMENT",
                    "title", "Private announcement", "href", "/app/inbox", "sourceType", "ANNOUNCEMENT", "sourceId", UUID.randomUUID()))))
                .andExpect(status().isCreated()).andReturn();
        var id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        org.mockito.Mockito.when(visibility.canView(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        mockMvc.perform(get("/api/v1/me/notifications").header(AuthHeaders.USER_ID, userId)
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(0));
        mockMvc.perform(get("/api/v1/me/notifications/unread-count").header(AuthHeaders.USER_ID, userId)
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
        mockMvc.perform(post("/api/v1/me/notifications/{id}/read", id).header(AuthHeaders.USER_ID, userId)
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)).andExpect(status().isNotFound());
    }

    @Test
    void eventReplayAndOlderRevisionPreserveSingleReadNotification() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(Map.of("userId", userId, "kind", "ANNOUNCEMENT",
                "title", "Current title", "href", "/app/inbox", "sourceType", "ANNOUNCEMENT", "sourceId", sourceId));
        String key = "NOTIFICATION:" + userId + ":ANNOUNCEMENT:" + sourceId + ":ANNOUNCEMENT";
        var event = new com.chanter.common.events.DurableEvent(UUID.randomUUID(), 1, "community", 2, "NOTIFICATION", key, payload);
        mockMvc.perform(post("/api/v1/internal/events").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event))).andExpect(status().isUnauthorized());
        for (int delivery = 0; delivery < 2; delivery++) {
            mockMvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(event))).andExpect(status().isNoContent());
        }
        var list = mockMvc.perform(get("/api/v1/me/notifications").header(AuthHeaders.USER_ID, userId)
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1)).andReturn();
        String id = objectMapper.readTree(list.getResponse().getContentAsString()).path("notifications").get(0).path("id").asText();
        mockMvc.perform(post("/api/v1/me/notifications/{id}/read", id).header(AuthHeaders.USER_ID, userId)
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)).andExpect(status().isOk());
        var old = new com.chanter.common.events.DurableEvent(UUID.randomUUID(), 1, "community", 1, "NOTIFICATION", key, payload.replace("Current title", "Old title"));
        mockMvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(old))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/me/notifications").header(AuthHeaders.USER_ID, userId)
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[0].title").value("Current title"))
                .andExpect(jsonPath("$.notifications[0].unread").value(false));
    }

    @Test
    void meEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me/notifications"))
                .andExpect(status().isUnauthorized());
    }
}
