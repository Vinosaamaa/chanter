package com.chanter.realtime.infra;

import com.chanter.realtime.application.DirectMessageCallStore;
import com.chanter.realtime.domain.DirectMessageCall;
import com.chanter.realtime.domain.DirectMessageCallStatus;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryDirectMessageCallStore implements DirectMessageCallStore {

    private final Map<UUID, DirectMessageCall> callsById = new ConcurrentHashMap<>();

    @Override
    public Optional<DirectMessageCall> findById(UUID callId) {
        return Optional.ofNullable(callsById.get(callId))
                .filter(call -> call.status() != DirectMessageCallStatus.ENDED);
    }

    @Override
    public Optional<DirectMessageCall> findActiveCallForUser(UUID userId) {
        return callsById.values().stream()
                .filter(call -> call.status() != DirectMessageCallStatus.ENDED)
                .filter(call -> call.callerUserId().equals(userId) || call.calleeUserId().equals(userId))
                .findFirst();
    }

    @Override
    public void save(DirectMessageCall call) {
        callsById.put(call.callId(), call);
    }

    @Override
    public void remove(UUID callId) {
        callsById.remove(callId);
    }

    public void clear() {
        callsById.clear();
    }
}
