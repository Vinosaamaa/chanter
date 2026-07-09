package com.chanter.realtime.infra;

import com.chanter.realtime.application.DirectMessageCallAuthorizer;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
@Profile("test")
public class TestDirectMessageCallAuthorizer implements DirectMessageCallAuthorizer {

    private final SocialGraph socialGraph;

    public TestDirectMessageCallAuthorizer(SocialGraph socialGraph) {
        this.socialGraph = socialGraph;
    }

    @Override
    public Mono<Void> requireCallAccess(UUID callerUserId, UUID calleeUserId) {
        return Mono.fromRunnable(() -> {
            if (callerUserId.equals(calleeUserId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users cannot call themselves");
            }
            if (socialGraph.isBlocked(callerUserId, calleeUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Direct Message calls are blocked");
            }
            if (!socialGraph.areFriends(callerUserId, calleeUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Direct Message calls require an accepted Friend Request");
            }
        });
    }
}
