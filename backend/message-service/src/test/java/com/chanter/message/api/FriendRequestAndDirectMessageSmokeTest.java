package com.chanter.message.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
class FriendRequestAndDirectMessageSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void friendsCanExchangeDirectMessagesAfterAcceptedFriendRequest() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        MvcResult friendRequestResult = mockMvc.perform(post("/api/v1/friend-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderUserId", userA.toString(),
                                "recipientUserId", userB.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        FriendRequestResponse friendRequest = objectMapper.readValue(
                friendRequestResult.getResponse().getContentAsString(),
                FriendRequestResponse.class
        );

        assertThat(friendRequest.senderUserId()).isEqualTo(userA);
        assertThat(friendRequest.recipientUserId()).isEqualTo(userB);
        assertThat(friendRequest.status()).isEqualTo("PENDING");

        mockMvc.perform(post("/api/v1/friend-requests/{friendRequestId}/acceptance", friendRequest.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientUserId", userB.toString()
                        ))))
                .andExpect(status().isOk());

        MvcResult sendResult = mockMvc.perform(post("/api/v1/direct-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderUserId", userA.toString(),
                                "recipientUserId", userB.toString(),
                                "body", "Want to study together?"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        DirectMessageResponse sent = objectMapper.readValue(
                sendResult.getResponse().getContentAsString(),
                DirectMessageResponse.class
        );

        assertThat(sent.senderUserId()).isEqualTo(userA);
        assertThat(sent.recipientUserId()).isEqualTo(userB);
        assertThat(sent.body()).isEqualTo("Want to study together?");

        MvcResult listResult = mockMvc.perform(get("/api/v1/direct-messages")
                        .param("viewerUserId", userB.toString())
                        .param("peerUserId", userA.toString()))
                .andExpect(status().isOk())
                .andReturn();
        DirectMessageListResponse listed = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                DirectMessageListResponse.class
        );

        assertThat(listed.messages()).containsExactly(sent);

        mockMvc.perform(post("/api/v1/direct-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderUserId", stranger.toString(),
                                "recipientUserId", userB.toString(),
                                "body", "Can we talk?"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void declinedFriendRequestDoesNotAllowDirectMessages() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        MvcResult friendRequestResult = mockMvc.perform(post("/api/v1/friend-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderUserId", userA.toString(),
                                "recipientUserId", userB.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        FriendRequestResponse friendRequest = objectMapper.readValue(
                friendRequestResult.getResponse().getContentAsString(),
                FriendRequestResponse.class
        );

        mockMvc.perform(post("/api/v1/friend-requests/{friendRequestId}/decline", friendRequest.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientUserId", userB.toString()
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/direct-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderUserId", userA.toString(),
                                "recipientUserId", userB.toString(),
                                "body", "Hello?"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void blockedUserCannotSendDirectMessage() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        MvcResult friendRequestResult = mockMvc.perform(post("/api/v1/friend-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderUserId", userA.toString(),
                                "recipientUserId", userB.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        FriendRequestResponse friendRequest = objectMapper.readValue(
                friendRequestResult.getResponse().getContentAsString(),
                FriendRequestResponse.class
        );

        mockMvc.perform(post("/api/v1/friend-requests/{friendRequestId}/acceptance", friendRequest.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientUserId", userB.toString()
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/user-blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "blockerUserId", userB.toString(),
                                "blockedUserId", userA.toString()
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/direct-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderUserId", userA.toString(),
                                "recipientUserId", userB.toString(),
                                "body", "Are you there?"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotSendFriendRequestWhenUsersAreAlreadyFriends() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        MvcResult friendRequestResult = mockMvc.perform(post("/api/v1/friend-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderUserId", userA.toString(),
                                "recipientUserId", userB.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        FriendRequestResponse friendRequest = objectMapper.readValue(
                friendRequestResult.getResponse().getContentAsString(),
                FriendRequestResponse.class
        );

        mockMvc.perform(post("/api/v1/friend-requests/{friendRequestId}/acceptance", friendRequest.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientUserId", userB.toString()
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/friend-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderUserId", userA.toString(),
                                "recipientUserId", userB.toString()
                        ))))
                .andExpect(status().isConflict());
    }

    @Test
    void usersCanRemoveFriendshipAndMustReRequestBeforeDirectMessages() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        MvcResult friendRequestResult = mockMvc.perform(post("/api/v1/friend-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderUserId", userA.toString(),
                                "recipientUserId", userB.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        FriendRequestResponse friendRequest = objectMapper.readValue(
                friendRequestResult.getResponse().getContentAsString(),
                FriendRequestResponse.class
        );

        mockMvc.perform(post("/api/v1/friend-requests/{friendRequestId}/acceptance", friendRequest.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientUserId", userB.toString()
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/friendships/status")
                        .param("userId", userA.toString())
                        .param("peerUserId", userB.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/friendships/removal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requesterUserId", userA.toString(),
                                "friendUserId", userB.toString()
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/direct-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderUserId", userA.toString(),
                                "recipientUserId", userB.toString(),
                                "body", "Hello again?"
                        ))))
                .andExpect(status().isForbidden());
    }

    private record FriendRequestResponse(
            UUID id,
            UUID senderUserId,
            UUID recipientUserId,
            String status
    ) {
    }

    private record DirectMessageResponse(
            UUID id,
            UUID senderUserId,
            UUID recipientUserId,
            String body
    ) {
    }

    private record DirectMessageListResponse(List<DirectMessageResponse> messages) {
    }
}
