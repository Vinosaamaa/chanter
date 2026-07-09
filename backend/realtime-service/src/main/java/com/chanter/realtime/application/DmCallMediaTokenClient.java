package com.chanter.realtime.application;

import java.util.UUID;
import reactor.core.publisher.Mono;

public interface DmCallMediaTokenClient {

    Mono<DmCallMediaToken> issueForCall(UUID callId, UUID participantUserId);
}
