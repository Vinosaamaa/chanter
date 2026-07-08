package com.chanter.realtime.websocket;

import com.chanter.realtime.application.DirectMessageClient;
import com.chanter.realtime.application.PersistedDirectMessage;
import com.chanter.realtime.application.PresenceStore;
import com.chanter.realtime.application.SocialFriendsClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class SocialRealtimeHub {

    private final SocialFriendsClient socialFriendsClient;
    private final DirectMessageClient directMessageClient;
    private final PresenceStore presenceStore;
    private final ObjectMapper objectMapper;
    private final Map<UUID, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, UUID> userBySession = new ConcurrentHashMap<>();

    public SocialRealtimeHub(
            SocialFriendsClient socialFriendsClient,
            DirectMessageClient directMessageClient,
            PresenceStore presenceStore,
            ObjectMapper objectMapper
    ) {
        this.socialFriendsClient = socialFriendsClient;
        this.directMessageClient = directMessageClient;
        this.presenceStore = presenceStore;
        this.objectMapper = objectMapper;
    }

    public Mono<Void> connect(WebSocketSession session, UUID userId) {
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        userBySession.put(session.getId(), userId);

        boolean wasOffline = !presenceStore.isOnline(userId);
        presenceStore.markOnline(userId);

        Mono<Void> socialSubscribed = sendJson(session, Map.of("type", "social_subscribed"));
        if (!wasOffline) {
            return socialSubscribed;
        }

        return socialSubscribed.then(notifyFriendsPresence(userId, "online"));
    }

    public Mono<Void> disconnect(WebSocketSession session) {
        UUID userId = userBySession.remove(session.getId());
        if (userId == null) {
            return Mono.empty();
        }

        Set<WebSocketSession> userSessions = sessionsByUser.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                sessionsByUser.remove(userId);
                presenceStore.markOffline(userId);
                return notifyFriendsPresence(userId, "offline");
            }
        }

        return Mono.empty();
    }

    public Mono<Void> sendDirectMessage(UUID senderUserId, UUID recipientUserId, String body) {
        PersistedDirectMessage message = directMessageClient.sendDirectMessage(senderUserId, recipientUserId, body);
        return publishDirectMessage(message);
    }

    public Mono<Void> publishDirectMessage(PersistedDirectMessage message) {
        return Flux.merge(
                deliverToUser(message.senderUserId(), message),
                deliverToUser(message.recipientUserId(), message)
        ).then();
    }

    private Mono<Void> notifyFriendsPresence(UUID userId, String status) {
        List<UUID> friendUserIds = socialFriendsClient.listFriendUserIds(userId);
        return Flux.fromIterable(friendUserIds)
                .flatMap(friendUserId -> deliverPresence(friendUserId, userId, status))
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

        return Flux.fromIterable(sessions)
                .flatMap(session -> sendJson(session, payload))
                .then();
    }

    private Mono<Void> deliverToUser(UUID userId, PersistedDirectMessage message) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return Mono.empty();
        }

        Map<String, Object> payload = Map.of(
                "type", "dm_message",
                "payload", Map.of(
                        "id", message.id().toString(),
                        "senderUserId", message.senderUserId().toString(),
                        "recipientUserId", message.recipientUserId().toString(),
                        "body", message.body(),
                        "sentAt", message.sentAt().toString()
                )
        );

        return Flux.fromIterable(sessions)
                .flatMap(session -> sendJson(session, payload))
                .then();
    }

    private Mono<Void> sendJson(WebSocketSession session, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return session.send(Mono.just(session.textMessage(json)))
                    .onErrorResume(error -> Mono.empty());
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }
}
