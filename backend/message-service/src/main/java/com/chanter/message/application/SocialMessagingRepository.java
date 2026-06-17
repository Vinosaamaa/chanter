package com.chanter.message.application;

import com.chanter.message.domain.DirectMessage;
import com.chanter.message.domain.FriendRequest;
import com.chanter.message.domain.FriendRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialMessagingRepository {

    FriendRequest saveFriendRequest(FriendRequest friendRequest);

    Optional<FriendRequest> findFriendRequestById(UUID friendRequestId);

    FriendRequest updateFriendRequestStatus(UUID friendRequestId, FriendRequestStatus status);

    boolean areFriends(UUID firstUserId, UUID secondUserId);

    boolean isBlocked(UUID senderUserId, UUID recipientUserId);

    void saveUserBlock(UUID blockerUserId, UUID blockedUserId);

    DirectMessage saveDirectMessage(DirectMessage directMessage);

    List<DirectMessage> findDirectMessages(UUID viewerUserId, UUID peerUserId);
}
