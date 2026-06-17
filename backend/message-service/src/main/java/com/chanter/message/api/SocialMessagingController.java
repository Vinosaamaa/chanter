package com.chanter.message.api;

import com.chanter.common.ServiceInfo;
import com.chanter.message.application.SocialMessagingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
            @Valid @RequestBody CreateFriendRequestRequest request
    ) {
        FriendRequestResponse response = FriendRequestResponse.from(
                socialMessagingService.sendFriendRequest(request.senderUserId(), request.recipientUserId())
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
            @Valid @RequestBody RespondToFriendRequestRequest request
    ) {
        return FriendRequestResponse.from(
                socialMessagingService.acceptFriendRequest(friendRequestId, request.recipientUserId())
        );
    }

    @PostMapping("/friend-requests/{friendRequestId}/decline")
    public FriendRequestResponse declineFriendRequest(
            @PathVariable UUID friendRequestId,
            @Valid @RequestBody RespondToFriendRequestRequest request
    ) {
        return FriendRequestResponse.from(
                socialMessagingService.declineFriendRequest(friendRequestId, request.recipientUserId())
        );
    }

    @PostMapping("/user-blocks")
    public ResponseEntity<Void> blockUser(@Valid @RequestBody CreateUserBlockRequest request) {
        socialMessagingService.blockUser(request.blockerUserId(), request.blockedUserId());
        return ResponseEntity.status(201).build();
    }

    @PostMapping("/direct-messages")
    public ResponseEntity<DirectMessageResponse> sendDirectMessage(
            @Valid @RequestBody CreateDirectMessageRequest request
    ) {
        DirectMessageResponse response = DirectMessageResponse.from(
                socialMessagingService.sendDirectMessage(
                        request.senderUserId(),
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
            @RequestParam UUID viewerUserId,
            @RequestParam UUID peerUserId
    ) {
        return DirectMessageListResponse.from(
                socialMessagingService.findDirectMessages(viewerUserId, peerUserId)
        );
    }
}
