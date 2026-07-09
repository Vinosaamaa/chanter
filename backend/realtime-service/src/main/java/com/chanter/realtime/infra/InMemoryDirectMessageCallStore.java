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
    private final Map<UUID, UUID> activeCallIdByUser = new ConcurrentHashMap<>();

    @Override
    public Optional<DirectMessageCall> findById(UUID callId) {
        return Optional.ofNullable(callsById.get(callId))
                .filter(call -> call.status() != DirectMessageCallStatus.ENDED);
    }

    @Override
    public Optional<DirectMessageCall> findActiveCallForUser(UUID userId) {
        UUID callId = activeCallIdByUser.get(userId);
        if (callId == null) {
            return Optional.empty();
        }
        return findById(callId);
    }

    @Override
    public Optional<DirectMessageCall> tryCreateRingingCall(DirectMessageCall call) {
        synchronized (this) {
            if (activeCallIdByUser.containsKey(call.callerUserId())
                    || activeCallIdByUser.containsKey(call.calleeUserId())) {
                return Optional.empty();
            }
            callsById.put(call.callId(), call);
            activeCallIdByUser.put(call.callerUserId(), call.callId());
            activeCallIdByUser.put(call.calleeUserId(), call.callId());
            return Optional.of(call);
        }
    }

    @Override
    public Optional<DirectMessageCall> activateIfRinging(UUID callId, UUID calleeUserId) {
        synchronized (this) {
            DirectMessageCall call = callsById.get(callId);
            if (call == null
                    || call.status() != DirectMessageCallStatus.RINGING
                    || !call.calleeUserId().equals(calleeUserId)) {
                return Optional.empty();
            }
            DirectMessageCall active = new DirectMessageCall(
                    call.callId(),
                    call.callerUserId(),
                    call.calleeUserId(),
                    DirectMessageCallStatus.ACTIVE,
                    call.createdAt()
            );
            callsById.put(callId, active);
            return Optional.of(active);
        }
    }

    @Override
    public Optional<DirectMessageCall> endIfPresent(UUID callId) {
        synchronized (this) {
            DirectMessageCall call = callsById.get(callId);
            if (call == null || call.status() == DirectMessageCallStatus.ENDED) {
                return Optional.empty();
            }
            callsById.remove(callId);
            activeCallIdByUser.remove(call.callerUserId(), callId);
            activeCallIdByUser.remove(call.calleeUserId(), callId);
            return Optional.of(call);
        }
    }

    @Override
    public Optional<DirectMessageCall> endIfRinging(UUID callId) {
        synchronized (this) {
            DirectMessageCall call = callsById.get(callId);
            if (call == null || call.status() != DirectMessageCallStatus.RINGING) {
                return Optional.empty();
            }
            return endIfPresent(callId);
        }
    }

    @Override
    public void remove(UUID callId) {
        endIfPresent(callId);
    }

    public void clear() {
        synchronized (this) {
            callsById.clear();
            activeCallIdByUser.clear();
        }
    }
}
