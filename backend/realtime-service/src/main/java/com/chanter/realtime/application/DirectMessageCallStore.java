package com.chanter.realtime.application;

import com.chanter.realtime.domain.DirectMessageCall;
import java.util.Optional;
import java.util.UUID;

public interface DirectMessageCallStore {

    Optional<DirectMessageCall> findById(UUID callId);

    Optional<DirectMessageCall> findActiveCallForUser(UUID userId);

    void save(DirectMessageCall call);

    void remove(UUID callId);
}
