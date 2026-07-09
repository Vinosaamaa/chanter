package com.chanter.realtime.websocket;

import com.chanter.realtime.application.DirectMessageCallAuthorizer;
import com.chanter.realtime.application.DirectMessageCallStore;
import com.chanter.realtime.application.DmCallMediaToken;
import com.chanter.realtime.application.DmCallMediaTokenClient;
import com.chanter.realtime.domain.DirectMessageCall;
import com.chanter.realtime.domain.DirectMessageCallStatus;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class DirectMessageCallHub {

    private static final Logger log = LoggerFactory.getLogger(DirectMessageCallHub.class);
    private static final long RING_TIMEOUT_SECONDS = 30;

    private final SocialRealtimeHub socialRealtimeHub;
    private final DirectMessageCallStore callStore;
    private final DirectMessageCallAuthorizer callAuthorizer;
    private final DmCallMediaTokenClient mediaTokenClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DirectMessageCallHub(
            SocialRealtimeHub socialRealtimeHub,
            DirectMessageCallStore callStore,
            DirectMessageCallAuthorizer callAuthorizer,
            DmCallMediaTokenClient mediaTokenClient
    ) {
        this.socialRealtimeHub = socialRealtimeHub;
        this.callStore = callStore;
        this.callAuthorizer = callAuthorizer;
        this.mediaTokenClient = mediaTokenClient;
    }

    @PreDestroy
    void shutdownScheduler() {
        scheduler.shutdownNow();
    }

    public Mono<Void> invite(UUID callerUserId, UUID calleeUserId) {
        return callAuthorizer.requireCallAccess(callerUserId, calleeUserId)
                .then(Mono.fromCallable(() -> {
                    if (callStore.findActiveCallForUser(callerUserId).isPresent()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Caller is already in a call");
                    }

                    UUID callId = UUID.randomUUID();
                    DirectMessageCall call = new DirectMessageCall(
                            callId,
                            callerUserId,
                            calleeUserId,
                            DirectMessageCallStatus.RINGING,
                            Instant.now()
                    );
                    Optional<DirectMessageCall> created = callStore.tryCreateRingingCall(call);
                    if (created.isEmpty()) {
                        return Optional.<DirectMessageCall>empty();
                    }

                    scheduleRingTimeout(callId);
                    log.info(
                            "DM call ringing callId={} caller={} callee={}",
                            callId,
                            callerUserId,
                            calleeUserId
                    );
                    return created;
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(maybeCall -> {
                    if (maybeCall.isEmpty()) {
                        return socialRealtimeHub.deliverEventToUser(callerUserId, Map.of(
                                "type", "call_busy",
                                "userId", calleeUserId.toString()
                        ));
                    }
                    DirectMessageCall call = maybeCall.get();
                    return socialRealtimeHub.deliverEventToUser(callerUserId, ringingPayload(call, "outgoing"))
                            .then(socialRealtimeHub.deliverEventToUser(
                                    call.calleeUserId(),
                                    ringingPayload(call, "incoming")
                            ));
                });
    }

    public Mono<Void> accept(UUID calleeUserId, UUID callId) {
        return Mono.fromCallable(() -> callStore.activateIfRinging(callId, calleeUserId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Call is not ringing")))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(call -> {
                    log.info("DM call accepted callId={}", callId);
                    Map<String, Object> accepted = Map.of(
                            "type", "call_accepted",
                            "callId", callId.toString(),
                            "callerUserId", call.callerUserId().toString(),
                            "calleeUserId", call.calleeUserId().toString()
                    );
                    return socialRealtimeHub.deliverEventToUser(call.callerUserId(), accepted)
                            .then(socialRealtimeHub.deliverEventToUser(call.calleeUserId(), accepted));
                });
    }

    public Mono<Void> decline(UUID userId, UUID callId) {
        return endCall(userId, callId, "declined");
    }

    public Mono<Void> cancel(UUID callerUserId, UUID callId) {
        return Mono.fromCallable(() -> {
                    DirectMessageCall call = resolveRingingCall(callId, callerUserId);
                    if (!call.callerUserId().equals(callerUserId)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the caller can cancel a ringing call");
                    }
                    return call;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(call -> endCall(call.callId(), "cancelled"));
    }

    public Mono<Void> hangUp(UUID userId, UUID callId) {
        return endCall(userId, callId, "ended");
    }

    public Mono<DmCallMediaToken> issueMediaToken(UUID userId, UUID callId) {
        return Mono.fromCallable(() -> {
                    DirectMessageCall call = callStore.findById(callId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Call not found"));

                    if (call.status() != DirectMessageCallStatus.ACTIVE) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Call is not active");
                    }
                    if (!call.callerUserId().equals(userId) && !call.calleeUserId().equals(userId)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a participant in this call");
                    }
                    return call;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(call -> mediaTokenClient.issueForCall(callId, userId));
    }

    private Mono<Void> endCall(UUID userId, UUID callId, String reason) {
        return Mono.fromCallable(() -> {
                    resolveAnyCall(callId, userId);
                    return callId;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(id -> endCall(id, reason));
    }

    private Mono<Void> endCall(UUID callId, String reason) {
        return Mono.fromCallable(() -> callStore.endIfPresent(callId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(maybeCall -> maybeCall.map(call -> deliverCallEnded(call, reason)).orElse(Mono.empty()));
    }

    private Mono<Void> deliverCallEnded(DirectMessageCall call, String reason) {
        log.info("DM call ended callId={} reason={}", call.callId(), reason);

        Map<String, Object> ended = Map.of(
                "type", "call_ended",
                "callId", call.callId().toString(),
                "reason", reason
        );
        return socialRealtimeHub.deliverEventToUser(call.callerUserId(), ended)
                .then(socialRealtimeHub.deliverEventToUser(call.calleeUserId(), ended));
    }

    private DirectMessageCall resolveRingingCall(UUID callId, UUID participantUserId) {
        DirectMessageCall call = resolveAnyCall(callId, participantUserId);
        if (call.status() != DirectMessageCallStatus.RINGING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Call is not ringing");
        }
        return call;
    }

    private DirectMessageCall resolveAnyCall(UUID callId, UUID participantUserId) {
        DirectMessageCall call = callStore.findById(callId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Call not found"));
        if (!call.callerUserId().equals(participantUserId) && !call.calleeUserId().equals(participantUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a participant in this call");
        }
        return call;
    }

    private void scheduleRingTimeout(UUID callId) {
        scheduler.schedule(
                () -> callStore.endIfRinging(callId)
                        .ifPresent(call -> deliverCallEnded(call, "timeout").subscribe()),
                RING_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private static Map<String, Object> ringingPayload(DirectMessageCall call, String direction) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "call_ringing");
        payload.put("callId", call.callId().toString());
        payload.put("callerUserId", call.callerUserId().toString());
        payload.put("calleeUserId", call.calleeUserId().toString());
        payload.put("direction", direction);
        return payload;
    }
}
