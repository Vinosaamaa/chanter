package com.chanter.message.application;

import com.chanter.message.domain.DirectMessage;
import com.chanter.message.domain.FriendRequest;
import com.chanter.message.domain.FriendRequestStatus;
import com.chanter.message.domain.FriendSummary;
import com.chanter.message.domain.FriendshipSnapshot;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SocialMessagingService {

    private final SocialMessagingRepository repository;
    private final CoMembershipClient coMembershipClient;
    private final Clock clock;

    public SocialMessagingService(
            SocialMessagingRepository repository,
            CoMembershipClient coMembershipClient,
            Clock clock
    ) {
        this.repository = repository;
        this.coMembershipClient = coMembershipClient;
        this.clock = clock;
    }

    @Transactional
    public FriendRequest sendFriendRequest(UUID senderUserId, UUID recipientUserId) {
        if (senderUserId.equals(recipientUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users cannot send Friend Requests to themselves");
        }
        if (repository.isBlocked(senderUserId, recipientUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Friend Requests are blocked between these users");
        }
        if (repository.areFriends(senderUserId, recipientUserId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Users are already friends");
        }
        if (repository.hasPendingFriendRequest(senderUserId, recipientUserId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A Friend Request is already pending between these users");
        }
        if (!coMembershipClient.shareStudyServerMembership(senderUserId, recipientUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Friend Requests require shared Study Server membership"
            );
        }

        try {
            return repository.saveFriendRequest(new FriendRequest(
                    UUID.randomUUID(),
                    senderUserId,
                    recipientUserId,
                    FriendRequestStatus.PENDING,
                    clock.instant()
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A Friend Request is already pending between these users");
        }
    }

    @Transactional
    public FriendRequest acceptFriendRequest(UUID friendRequestId, UUID recipientUserId) {
        FriendRequest friendRequest = requireFriendRequest(friendRequestId);
        requireRecipient(friendRequest, recipientUserId);

        if (friendRequest.status() != FriendRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Friend Request is not pending");
        }

        return repository.updateFriendRequestStatus(friendRequestId, FriendRequestStatus.ACCEPTED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Friend Request is no longer pending"));
    }

    @Transactional
    public FriendRequest declineFriendRequest(UUID friendRequestId, UUID recipientUserId) {
        FriendRequest friendRequest = requireFriendRequest(friendRequestId);
        requireRecipient(friendRequest, recipientUserId);

        if (friendRequest.status() != FriendRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Friend Request is not pending");
        }

        return repository.updateFriendRequestStatus(friendRequestId, FriendRequestStatus.DECLINED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Friend Request is no longer pending"));
    }

    public FriendshipSnapshot findFriendshipStatus(UUID viewerUserId, UUID peerUserId) {
        if (viewerUserId.equals(peerUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users cannot query friendship with themselves");
        }

        return repository.findFriendshipSnapshot(viewerUserId, peerUserId);
    }

    public List<FriendSummary> findFriends(UUID viewerUserId) {
        return repository.findFriends(viewerUserId);
    }

    @Transactional
    public void removeFriendship(UUID requesterUserId, UUID friendUserId) {
        if (requesterUserId.equals(friendUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users cannot remove themselves as a friend");
        }
        if (!repository.areFriends(requesterUserId, friendUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users are not friends");
        }

        repository.removeFriendship(requesterUserId, friendUserId);
    }

    public void blockUser(UUID blockerUserId, UUID blockedUserId) {
        if (blockerUserId.equals(blockedUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users cannot block themselves");
        }

        repository.saveUserBlock(blockerUserId, blockedUserId);
    }

    @Transactional
    public DirectMessage sendDirectMessage(UUID senderUserId, UUID recipientUserId, String body) {
        if (senderUserId.equals(recipientUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users cannot send Direct Messages to themselves");
        }
        if (repository.isBlocked(senderUserId, recipientUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Direct Messages are blocked between these users");
        }
        if (!repository.areFriends(senderUserId, recipientUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Direct Messages require an accepted Friend Request");
        }

        return repository.saveDirectMessage(new DirectMessage(
                UUID.randomUUID(),
                senderUserId,
                recipientUserId,
                body.trim(),
                clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
    }

    public List<DirectMessage> findDirectMessages(UUID viewerUserId, UUID peerUserId) {
        if (!repository.areFriends(viewerUserId, peerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Direct Messages require an accepted Friend Request");
        }
        if (repository.isBlocked(viewerUserId, peerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Direct Messages are blocked between these users");
        }

        return repository.findDirectMessages(viewerUserId, peerUserId);
    }

    private FriendRequest requireFriendRequest(UUID friendRequestId) {
        return repository.findFriendRequestById(friendRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Friend Request not found"));
    }

    private void requireRecipient(FriendRequest friendRequest, UUID recipientUserId) {
        if (!friendRequest.recipientUserId().equals(recipientUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the recipient can respond to this Friend Request");
        }
    }
}
