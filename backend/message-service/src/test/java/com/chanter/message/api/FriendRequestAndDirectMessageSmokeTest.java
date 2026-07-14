package com.chanter.message.api;

import static com.chanter.message.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.message.infra.TestCoMembershipClient;
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
class FriendRequestAndDirectMessageSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestCoMembershipClient coMembershipClient;

    @BeforeEach
    void setUp() {
        coMembershipClient.clear();
    }

    @Test
    void friendsCanExchangeDirectMessagesAfterAcceptedFriendRequest() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        FriendRequestResponse friendRequest = sendFriendRequest(userA, userB);

        assertThat(friendRequest.senderUserId()).isEqualTo(userA);
        assertThat(friendRequest.recipientUserId()).isEqualTo(userB);
        assertThat(friendRequest.status()).isEqualTo("PENDING");

        acceptFriendRequest(friendRequest.id(), userB);

        DirectMessageResponse sent = sendDirectMessage(userA, userB, "Want to study together?");

        assertThat(sent.senderUserId()).isEqualTo(userA);
        assertThat(sent.recipientUserId()).isEqualTo(userB);
        assertThat(sent.body()).isEqualTo("Want to study together?");

        MvcResult listResult = mockMvc.perform(get("/api/v1/direct-messages")
                        .with(asUser(userB))
                        .param("peerUserId", userA.toString()))
                .andExpect(status().isOk())
                .andReturn();
        DirectMessageListResponse listed = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                DirectMessageListResponse.class
        );

        assertThat(listed.messages()).containsExactly(sent);

        mockMvc.perform(post("/api/v1/direct-messages")
                        .with(asUser(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientUserId", userB.toString(),
                                "body", "Can we talk?"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void declinedFriendRequestDoesNotAllowDirectMessages() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        FriendRequestResponse friendRequest = sendFriendRequest(userA, userB);

        mockMvc.perform(post("/api/v1/friend-requests/{friendRequestId}/decline", friendRequest.id())
                        .with(asUser(userB)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/direct-messages")
                        .with(asUser(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientUserId", userB.toString(),
                                "body", "Hello?"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void blockedUserCannotSendDirectMessage() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        FriendRequestResponse friendRequest = sendFriendRequest(userA, userB);
        acceptFriendRequest(friendRequest.id(), userB);

        mockMvc.perform(post("/api/v1/user-blocks")
                        .with(asUser(userB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "blockedUserId", userA.toString()
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/direct-messages")
                        .with(asUser(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientUserId", userB.toString(),
                                "body", "Are you there?"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void blockedUserCannotReadDirectMessages() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        FriendRequestResponse friendRequest = sendFriendRequest(userA, userB);
        acceptFriendRequest(friendRequest.id(), userB);
        sendDirectMessage(userA, userB, "Before block");

        mockMvc.perform(post("/api/v1/user-blocks")
                        .with(asUser(userB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "blockedUserId", userA.toString()
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/direct-messages")
                        .with(asUser(userA))
                        .param("peerUserId", userB.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void blockedUserCannotSendFriendRequest() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/user-blocks")
                        .with(asUser(userB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "blockedUserId", userA.toString()
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/friend-requests")
                        .with(asUser(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientUserId", userB.toString()
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotSendFriendRequestWhenUsersAreAlreadyFriends() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        FriendRequestResponse friendRequest = sendFriendRequest(userA, userB);
        acceptFriendRequest(friendRequest.id(), userB);

        mockMvc.perform(post("/api/v1/friend-requests")
                        .with(asUser(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientUserId", userB.toString()
                        ))))
                .andExpect(status().isConflict());
    }

    @Test
    void usersCanDeclineResendAndDeclineAgain() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        FriendRequestResponse firstRequest = sendFriendRequest(userA, userB);

        mockMvc.perform(post("/api/v1/friend-requests/{friendRequestId}/decline", firstRequest.id())
                        .with(asUser(userB)))
                .andExpect(status().isOk());

        FriendRequestResponse secondRequest = sendFriendRequest(userA, userB);

        mockMvc.perform(post("/api/v1/friend-requests/{friendRequestId}/decline", secondRequest.id())
                        .with(asUser(userB)))
                .andExpect(status().isOk());
    }

    @Test
    void cannotSendReversePendingFriendRequest() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        sendFriendRequest(userA, userB);

        mockMvc.perform(post("/api/v1/friend-requests")
                        .with(asUser(userB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientUserId", userA.toString()
                        ))))
                .andExpect(status().isConflict());
    }

    @Test
    void usersCanRemoveFriendshipAndMustReRequestBeforeDirectMessages() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        FriendRequestResponse friendRequest = sendFriendRequest(userA, userB);
        acceptFriendRequest(friendRequest.id(), userB);

        mockMvc.perform(get("/api/v1/friendships/status")
                        .with(asUser(userA))
                        .param("peerUserId", userB.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/friendships/removal")
                        .with(asUser(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "friendUserId", userB.toString()
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/direct-messages")
                        .with(asUser(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientUserId", userB.toString(),
                                "body", "Hello again?"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void friendRequestRequiresSharedStudyServerMembership() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        coMembershipClient.deny(userA, userB);

        mockMvc.perform(post("/api/v1/friend-requests")
                        .with(asUser(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientUserId", userB.toString()
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptedFriendsAppearOnFriendsListAndBlockedUsersAreExcluded() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID userC = UUID.randomUUID();

        FriendRequestResponse requestB = sendFriendRequest(userA, userB);
        acceptFriendRequest(requestB.id(), userB);

        FriendRequestResponse requestC = sendFriendRequest(userA, userC);
        acceptFriendRequest(requestC.id(), userC);

        mockMvc.perform(post("/api/v1/user-blocks")
                        .with(asUser(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "blockedUserId", userC.toString()
                        ))))
                .andExpect(status().isCreated());

        MvcResult friendsResult = mockMvc.perform(get("/api/v1/friendships").with(asUser(userA)))
                .andExpect(status().isOk())
                .andReturn();
        FriendsListResponse friends = objectMapper.readValue(
                friendsResult.getResponse().getContentAsString(),
                FriendsListResponse.class
        );

        assertThat(friends.friends())
                .extracting(FriendSummaryResponse::friendUserId)
                .containsExactly(userB);
    }

    @Test
    void directMessageCallEligibilityRequiresFriendshipAndRejectsBlocks() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/direct-message-calls/eligibility")
                        .with(asUser(userA))
                        .param("peerUserId", userB.toString()))
                .andExpect(status().isForbidden());

        FriendRequestResponse friendRequest = sendFriendRequest(userA, userB);
        acceptFriendRequest(friendRequest.id(), userB);

        mockMvc.perform(get("/api/v1/direct-message-calls/eligibility")
                        .with(asUser(userA))
                        .param("peerUserId", userB.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/user-blocks")
                        .with(asUser(userB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "blockedUserId", userA.toString()
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/direct-message-calls/eligibility")
                        .with(asUser(userA))
                        .param("peerUserId", userB.toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/direct-message-calls/eligibility")
                        .with(asUser(stranger))
                        .param("peerUserId", userB.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void usersCanListAcceptDeclineCancelAndBlockFriendRequests() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID userC = UUID.randomUUID();
        UUID userD = UUID.randomUUID();

        FriendRequestResponse requestFromA = sendFriendRequest(userA, userB);
        FriendRequestResponse requestFromC = sendFriendRequest(userC, userB);
        FriendRequestResponse requestFromB = sendFriendRequest(userB, userD);

        MvcResult inboxResult = mockMvc.perform(get("/api/v1/friend-requests").with(asUser(userB)))
                .andExpect(status().isOk())
                .andReturn();
        FriendRequestListResponse inboxForB = objectMapper.readValue(
                inboxResult.getResponse().getContentAsString(),
                FriendRequestListResponse.class
        );

        assertThat(inboxForB.incoming())
                .extracting(FriendRequestResponse::id)
                .containsExactlyInAnyOrder(requestFromA.id(), requestFromC.id());
        assertThat(inboxForB.outgoing())
                .extracting(FriendRequestResponse::id)
                .containsExactly(requestFromB.id());

        mockMvc.perform(post("/api/v1/friend-requests/{friendRequestId}/acceptance", requestFromA.id())
                        .with(asUser(userB)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/friend-requests/{friendRequestId}/decline", requestFromC.id())
                        .with(asUser(userB)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/friend-requests/{friendRequestId}/cancellation", requestFromB.id())
                        .with(asUser(userB)))
                .andExpect(status().isNoContent());

        MvcResult updatedInboxResult = mockMvc.perform(get("/api/v1/friend-requests").with(asUser(userB)))
                .andExpect(status().isOk())
                .andReturn();
        FriendRequestListResponse updatedInboxForB = objectMapper.readValue(
                updatedInboxResult.getResponse().getContentAsString(),
                FriendRequestListResponse.class
        );

        assertThat(updatedInboxForB.incoming()).isEmpty();
        assertThat(updatedInboxForB.outgoing()).isEmpty();

        FriendRequestResponse blockedRequest = sendFriendRequest(userC, userB);

        mockMvc.perform(post("/api/v1/user-blocks")
                        .with(asUser(userB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "blockedUserId", userC.toString()
                        ))))
                .andExpect(status().isCreated());

        MvcResult blockedUsersResult = mockMvc.perform(get("/api/v1/user-blocks")
                        .with(asUser(userB)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(blockedUsersResult.getResponse().getContentAsString())
                .contains(userC.toString())
                .doesNotContain(userA.toString());

        MvcResult blockedInboxResult = mockMvc.perform(get("/api/v1/friend-requests").with(asUser(userB)))
                .andExpect(status().isOk())
                .andReturn();
        FriendRequestListResponse inboxAfterBlock = objectMapper.readValue(
                blockedInboxResult.getResponse().getContentAsString(),
                FriendRequestListResponse.class
        );

        assertThat(inboxAfterBlock.incoming())
                .extracting(FriendRequestResponse::id)
                .doesNotContain(blockedRequest.id());
    }

    @Test
    void onlySenderCanCancelPendingFriendRequest() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        FriendRequestResponse friendRequest = sendFriendRequest(userA, userB);

        mockMvc.perform(post("/api/v1/friend-requests/{friendRequestId}/cancellation", friendRequest.id())
                        .with(asUser(userB)))
                .andExpect(status().isForbidden());
    }

    private FriendRequestResponse sendFriendRequest(UUID senderUserId, UUID recipientUserId) throws Exception {
        MvcResult friendRequestResult = mockMvc.perform(post("/api/v1/friend-requests")
                        .with(asUser(senderUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientUserId", recipientUserId.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(
                friendRequestResult.getResponse().getContentAsString(),
                FriendRequestResponse.class
        );
    }

    private void acceptFriendRequest(UUID friendRequestId, UUID recipientUserId) throws Exception {
        mockMvc.perform(post("/api/v1/friend-requests/{friendRequestId}/acceptance", friendRequestId)
                        .with(asUser(recipientUserId)))
                .andExpect(status().isOk());
    }

    private DirectMessageResponse sendDirectMessage(UUID senderUserId, UUID recipientUserId, String body)
            throws Exception {
        MvcResult sendResult = mockMvc.perform(post("/api/v1/direct-messages")
                        .with(asUser(senderUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientUserId", recipientUserId.toString(),
                                "body", body
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(
                sendResult.getResponse().getContentAsString(),
                DirectMessageResponse.class
        );
    }
}
