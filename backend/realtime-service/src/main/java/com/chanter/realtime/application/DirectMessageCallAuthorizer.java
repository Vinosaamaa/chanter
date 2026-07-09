package com.chanter.realtime.application;

import java.util.UUID;
import reactor.core.publisher.Mono;

public interface DirectMessageCallAuthorizer {

    Mono<Void> requireCallAccess(UUID callerUserId, UUID calleeUserId);
}
