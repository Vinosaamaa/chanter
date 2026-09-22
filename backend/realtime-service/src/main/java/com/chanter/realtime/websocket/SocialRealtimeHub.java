package com.chanter.realtime.websocket;

import com.chanter.realtime.application.DirectMessageClient;
import com.chanter.realtime.application.PersistedDirectMessage;
import com.chanter.realtime.application.PresenceStore;
import com.chanter.realtime.application.SocialFriendsClient;
import com.chanter.realtime.application.DirectMessageCallAuthorizer;
import com.chanter.common.auth.ModerationAccess;
import com.chanter.common.auth.ModerationAccess.Target;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class SocialRealtimeHub {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(SocialRealtimeHub.class);

    private final SocialFriendsClient socialFriendsClient;
    private final DirectMessageClient directMessageClient;
    private final PresenceStore presenceStore;
    private final ObjectMapper objectMapper;
    private final DirectMessageCallAuthorizer pairAccess;
    private final ModerationAccess moderation;
    private final Map<UUID, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, UUID> userBySession = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> connectionGenerations = new ConcurrentHashMap<>();
    private final Object sessionLock = new Object();

    public SocialRealtimeHub(
            SocialFriendsClient socialFriendsClient,
            DirectMessageClient directMessageClient,
            PresenceStore presenceStore,
            ObjectMapper objectMapper,
            DirectMessageCallAuthorizer pairAccess,
            ModerationAccess moderation
    ) {
        this.socialFriendsClient = socialFriendsClient;
        this.directMessageClient = directMessageClient;
        this.presenceStore = presenceStore;
        this.objectMapper = objectMapper;
        this.pairAccess = pairAccess;
        this.moderation = moderation;
    }

    /** Local authenticated socket count; no per-user telemetry dimensions. */
    public int connectionCount() { return userBySession.size(); }

    public Mono<Void> connect(WebSocketSession session, UUID userId) {
        boolean shouldAnnounceOnline;
        synchronized (sessionLock) {
            Set<WebSocketSession> userSessions = sessionsByUser.computeIfAbsent(
                    userId,
                    ignored -> ConcurrentHashMap.newKeySet()
            );
            shouldAnnounceOnline = userSessions.isEmpty();
            userSessions.add(session);
            userBySession.put(session.getId(), userId);
            connectionGenerations.computeIfAbsent(userId, ignored -> new AtomicInteger(0)).incrementAndGet();
        }

        return Mono.fromCallable(() -> {
                    if (shouldAnnounceOnline) {
                        presenceStore.markOnline(userId);
                    }
                    return shouldAnnounceOnline;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(announceOnline -> {
                    Mono<Void> socialSubscribed = sendJson(session, Map.of("type", "social_subscribed"));
                    if (!announceOnline) {
                        return socialSubscribed;
                    }
                    return socialSubscribed.then(
                            notifyFriendsPresence(userId, "online").onErrorResume(SocialRealtimeHub::presenceUnavailable)
                    );
                })
                .onErrorResume(error -> disconnect(session).then(Mono.error(error)));
    }

    public Mono<Void> disconnect(WebSocketSession session) {
        UUID userId = userBySession.remove(session.getId());
        if (userId == null) {
            return Mono.empty();
        }

        boolean becameOffline;
        int generationAtDisconnect;
        synchronized (sessionLock) {
            Set<WebSocketSession> userSessions = sessionsByUser.get(userId);
            if (userSessions == null) {
                return Mono.empty();
            }
            userSessions.remove(session);
            becameOffline = userSessions.isEmpty();
            if (becameOffline) {
                sessionsByUser.remove(userId);
            }
            generationAtDisconnect = connectionGenerations
                    .computeIfAbsent(userId, ignored -> new AtomicInteger(0))
                    .get();
        }

        if (!becameOffline) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
                    AtomicInteger generation = connectionGenerations.get(userId);
                    if (generation == null || generation.get() != generationAtDisconnect) {
                        return;
                    }
                    boolean stillOffline;
                    synchronized (sessionLock) {
                        stillOffline = !sessionsByUser.containsKey(userId);
                    }
                    if (!stillOffline || generation.get() != generationAtDisconnect) {
                        return;
                    }
                    presenceStore.markOffline(userId);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.defer(() -> {
                    AtomicInteger generation = connectionGenerations.get(userId);
                    if (generation == null || generation.get() != generationAtDisconnect) {
                        return Mono.empty();
                    }
                    return notifyFriendsPresence(userId, "offline").onErrorResume(SocialRealtimeHub::presenceUnavailable);
                }))
                .doFinally(signal -> {
                    synchronized (sessionLock) {
                        AtomicInteger activeGeneration = connectionGenerations.get(userId);
                        if (activeGeneration != null
                                && activeGeneration.get() == generationAtDisconnect
                                && !sessionsByUser.containsKey(userId)) {
                            connectionGenerations.remove(userId);
                        }
                    }
                });
    }

    public Mono<Void> sendDirectMessage(UUID senderUserId, UUID recipientUserId, String body) {
        return sendDirectMessage(senderUserId, recipientUserId, body, null);
    }

    public Mono<Void> sendDirectMessage(
            UUID senderUserId,
            UUID recipientUserId,
            String body,
            String clientMessageId
    ) {
        return Mono.fromCallable(() -> directMessageClient.sendDirectMessage(senderUserId, recipientUserId, body))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(message -> publishDirectMessage(message, clientMessageId));
    }

    public Mono<Void> publishDirectMessage(PersistedDirectMessage message) {
        return publishDirectMessage(message, null);
    }

    public Mono<Void> publishDirectMessage(PersistedDirectMessage message, String clientMessageId) {
        return pairAccess.requireCallAccess(message.senderUserId(),message.recipientUserId())
                .then(Mono.fromRunnable(() -> moderation.requireAllowed(message.senderUserId(),List.of(new Target("DM",message.id()))))
                        .subscribeOn(Schedulers.boundedElastic()))
                .thenMany(Flux.merge(
                deliverToUser(message.senderUserId(), message, clientMessageId),
                deliverToUser(message.recipientUserId(), message, clientMessageId)
        )).onErrorResume(SocialRealtimeHub::isAccessDenial, error -> Mono.empty()).then();
    }

    public Mono<Void> deliverEventToUser(UUID userId, Map<String, Object> payload) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> moderation.requireAccount(userId)).subscribeOn(Schedulers.boundedElastic())
                .thenMany(Flux.fromIterable(sessions))
                .flatMap(session -> sendJson(session, payload),4)
                .onErrorResume(SocialRealtimeHub::isAccessDenial, error -> Mono.empty())
                .then();
    }

    /** A replacement snapshot clears stale online state after blocking or suspension.
     * The cap and deadline fail closed under unusually large friend graphs or authority outages.
     */
    public Mono<Void> refreshPresence(WebSocketSession session, UUID user) {
        return socialFriendsClient.listFriendUserIds(user)
                .flatMapMany(ids -> Flux.fromIterable(ids).take(1000))
                .filterWhen(peer -> Mono.fromCallable(() -> presenceStore.isOnline(peer)).subscribeOn(Schedulers.boundedElastic()),4)
                .flatMap(peer -> pairAccess.requireCallAccess(user,peer).thenReturn(peer)
                        .onErrorResume(denied -> Mono.empty()),4)
                .collectList().timeout(java.time.Duration.ofSeconds(4))
                .onErrorReturn(List.of())
                .flatMap(online -> sendJson(session,Map.of("type","presence_snapshot","onlineUserIds",online)));
    }

    private static boolean isAccessDenial(Throwable failure) {
        return failure instanceof org.springframework.web.server.ResponseStatusException status
                && java.util.Set.of(401,403,404).contains(status.getStatusCode().value());
    }

    private static Mono<Void> presenceUnavailable(Throwable failure) {
        log.warn("Presence fanout unavailable; current presence snapshots will reconcile");
        return Mono.empty();
    }

    private Mono<Void> notifyFriendsPresence(UUID userId, String status) {
        return socialFriendsClient.listFriendUserIds(userId)
                .flatMapMany(Flux::fromIterable)
                .flatMap(friendUserId -> deliverPresence(friendUserId, userId, status),4)
                .then();
    }

    private Mono<Void> deliverPresence(UUID viewerUserId, UUID subjectUserId, String status) {
        Set<WebSocketSession> sessions = sessionsByUser.get(viewerUserId);
        if (sessions == null || sessions.isEmpty()) {
            return Mono.empty();
        }

        Map<String, Object> payload = Map.of(
                "type", "presence_changed",
                "userId", subjectUserId.toString(),
                "status", status
        );

        return pairAccess.requireCallAccess(viewerUserId,subjectUserId)
                .thenMany(Flux.fromIterable(sessions))
                .flatMap(session -> sendJson(session, payload),4)
                .onErrorResume(SocialRealtimeHub::isAccessDenial,error -> Mono.empty())
                .then();
    }

    private Mono<Void> deliverToUser(UUID userId, PersistedDirectMessage message, String clientMessageId) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return Mono.empty();
        }

        Map<String, Object> messagePayload = new LinkedHashMap<>();
        messagePayload.put("id", message.id().toString());
        messagePayload.put("senderUserId", message.senderUserId().toString());
        messagePayload.put("recipientUserId", message.recipientUserId().toString());
        messagePayload.put("body", message.body());
        messagePayload.put("sentAt", message.sentAt().toString());
        if (clientMessageId != null && !clientMessageId.isBlank()) {
            messagePayload.put("clientMessageId", clientMessageId);
        }

        Map<String, Object> payload = Map.of(
                "type", "dm_message",
                "payload", messagePayload
        );

        return Flux.fromIterable(sessions)
                .flatMap(session -> sendJson(session, payload),4)
                .then();
    }

    private Mono<Void> sendJson(WebSocketSession session, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return session.send(Mono.just(session.textMessage(json)));
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }
}
