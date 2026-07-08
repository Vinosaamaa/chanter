package com.chanter.realtime.infra;

import com.chanter.realtime.application.DirectMessageClient;
import com.chanter.realtime.application.PersistedDirectMessage;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("test")
public class TestDirectMessageClient implements DirectMessageClient {

    private final SocialGraph socialGraph;
    private final Clock clock;

    public TestDirectMessageClient(SocialGraph socialGraph, Clock clock) {
        this.socialGraph = socialGraph;
        this.clock = clock;
    }

    @Override
    public PersistedDirectMessage sendDirectMessage(UUID senderUserId, UUID recipientUserId, String body) {
        if (senderUserId.equals(recipientUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users cannot send Direct Messages to themselves");
        }
        if (!socialGraph.areFriends(senderUserId, recipientUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Direct Messages require an accepted Friend Request");
        }

        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Direct Message body must not be blank");
        }

        return new PersistedDirectMessage(
                UUID.randomUUID(),
                senderUserId,
                recipientUserId,
                trimmed,
                clock.instant()
        );
    }
}
