package com.chanter.realtime.application;

import com.chanter.realtime.domain.DirectMessageCall;
import java.util.Optional;
import java.util.UUID;

public interface DirectMessageCallStore {

    Optional<DirectMessageCall> findById(UUID callId);

    Optional<DirectMessageCall> findActiveCallForUser(UUID userId);

    Optional<DirectMessageCall> tryCreateRingingCall(DirectMessageCall call);

    Optional<DirectMessageCall> activateIfRinging(UUID callId, UUID calleeUserId);

    Optional<DirectMessageCall> endIfPresent(UUID callId);

    Optional<DirectMessageCall> endIfRinging(UUID callId);

    void remove(UUID callId);
}
