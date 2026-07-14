package com.chanter.message.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.message.application.SocialMessagingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX)
public class SocialMessagingController {

    private final SocialMessagingService socialMessagingService;

    public SocialMessagingController(SocialMessagingService socialMessagingService) {
        this.socialMessagingService = socialMessagingService;
    }

    @PostMapping("/friend-requests")
    public ResponseEntity<FriendRequestResponse> sendFriendRequest(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID senderUserId,
            @Valid @RequestBody CreateFriendRequestRequest request
    ) {
        FriendRequestResponse response = FriendRequestResponse.from(
                socialMessagingService.sendFriendRequest(senderUserId, request.recipientUserId())
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{friendRequestId}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/friend-requests/{friendRequestId}/acceptance")
    public FriendRequestResponse acceptFriendRequest(
            @PathVariable UUID friendRequestId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID recipientUserId
    ) {
        return FriendRequestResponse.from(
                socialMessagingService.acceptFriendRequest(friendRequestId, recipientUserId)
        );
    }

    @PostMapping("/friend-requests/{friendRequestId}/decline")
    public FriendRequestResponse declineFriendRequest(
            @PathVariable UUID friendRequestId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID recipientUserId
    ) {
        return FriendRequestResponse.from(
                socialMessagingService.declineFriendRequest(friendRequestId, recipientUserId)
        );
    }

    @GetMapping("/friend-requests")
    public FriendRequestListResponse findFriendRequests(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        return FriendRequestListResponse.from(
                socialMessagingService.findPendingIncomingFriendRequests(viewerUserId),
                socialMessagingService.findPendingOutgoingFriendRequests(viewerUserId)
        );
    }

    @PostMapping("/friend-requests/{friendRequestId}/cancellation")
    public ResponseEntity<Void> cancelFriendRequest(
            @PathVariable UUID friendRequestId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID senderUserId
    ) {
        socialMessagingService.cancelFriendRequest(friendRequestId, senderUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/user-blocks")
    public ResponseEntity<Void> blockUser(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID blockerUserId,
            @Valid @RequestBody CreateUserBlockRequest request
    ) {
        socialMessagingService.blockUser(blockerUserId, request.blockedUserId());
        return ResponseEntity.status(201).build();
    }

    @GetMapping("/user-blocks")
    public UserBlockListResponse findUserBlocks(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID blockerUserId
    ) {
        return new UserBlockListResponse(socialMessagingService.findBlockedUserIds(blockerUserId));
    }

    @GetMapping("/friendships")
    public FriendsListResponse findFriends(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        return FriendsListResponse.from(socialMessagingService.findFriends(viewerUserId));
    }

    @GetMapping("/friendships/status")
    public FriendshipStatusResponse findFriendshipStatus(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId,
            @RequestParam UUID peerUserId
    ) {
        return FriendshipStatusResponse.from(
                socialMessagingService.findFriendshipStatus(viewerUserId, peerUserId)
        );
    }

    @PostMapping("/friendships/removal")
    public ResponseEntity<Void> removeFriendship(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID requesterUserId,
            @Valid @RequestBody RemoveFriendshipRequest request
    ) {
        socialMessagingService.removeFriendship(requesterUserId, request.friendUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/direct-messages")
    public ResponseEntity<DirectMessageResponse> sendDirectMessage(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID senderUserId,
            @Valid @RequestBody CreateDirectMessageRequest request
    ) {
        DirectMessageResponse response = DirectMessageResponse.from(
                socialMessagingService.sendDirectMessage(
                        senderUserId,
                        request.recipientUserId(),
                        request.body()
                )
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{directMessageId}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/direct-messages")
    public DirectMessageListResponse findDirectMessages(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId,
            @RequestParam UUID peerUserId
    ) {
        return DirectMessageListResponse.from(
                socialMessagingService.findDirectMessages(viewerUserId, peerUserId)
        );
    }
}
