package com.chanter.realtime.websocket;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InvalidJwtException;
import com.chanter.common.auth.JwtTokenService;
import com.chanter.realtime.application.ChannelMessageClient;
import com.chanter.realtime.application.ChannelSubscriptionAuthorizer;
import com.chanter.realtime.application.PersistedChannelMessage;
import com.chanter.realtime.application.PresenceStore;
import com.chanter.realtime.application.SocialFriendsClient;
import com.chanter.realtime.domain.RealtimeChannelScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class RealtimeWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);

    private final JwtTokenService jwtTokenService;
    private final ChannelSubscriptionAuthorizer subscriptionAuthorizer;
    private final ChannelMessageClient channelMessageClient;
    private final RealtimeSubscriptionHub subscriptionHub;
    private final SocialRealtimeHub socialRealtimeHub;
    private final SocialFriendsClient socialFriendsClient;
    private final PresenceStore presenceStore;
    private final ObjectMapper objectMapper;

    public RealtimeWebSocketHandler(
            JwtTokenService jwtTokenService,
            ChannelSubscriptionAuthorizer subscriptionAuthorizer,
            ChannelMessageClient channelMessageClient,
            RealtimeSubscriptionHub subscriptionHub,
            SocialRealtimeHub socialRealtimeHub,
            SocialFriendsClient socialFriendsClient,
            PresenceStore presenceStore,
            ObjectMapper objectMapper
    ) {
        this.jwtTokenService = jwtTokenService;
        this.subscriptionAuthorizer = subscriptionAuthorizer;
        this.channelMessageClient = channelMessageClient;
        this.subscriptionHub = subscriptionHub;
        this.socialRealtimeHub = socialRealtimeHub;
        this.socialFriendsClient = socialFriendsClient;
        this.presenceStore = presenceStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        UUID userId = authenticate(session);
        if (userId == null) {
            return session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Authentication required"));
        }

        return Mono.usingWhen(
                Mono.just(session),
                activeSession -> Mono.defer(() -> socialRealtimeHub.connect(activeSession, userId)
                                .then(sendInitialFriendPresence(activeSession, userId).onErrorResume(error -> {
                                    log.warn(
                                            "Initial friend presence snapshot unavailable for user {}",
                                            userId,
                                            error
                                    );
                                    return Mono.empty();
                                })))
                        .subscribeOn(Schedulers.boundedElastic())
                        .thenMany(activeSession.receive()
                                .map(WebSocketMessage::getPayloadAsText)
                                .concatMap(payload -> handleClientFrame(activeSession, userId, payload)))
                        .then(),
                activeSession -> socialRealtimeHub.disconnect(activeSession)
                        .onErrorResume(error -> {
                            log.warn("Social disconnect cleanup failed for session {}", activeSession.getId(), error);
                            return Mono.empty();
                        })
                        .then(Mono.fromRunnable(() -> subscriptionHub.unsubscribeAll(activeSession)))
        );
    }

    private Mono<Void> handleClientFrame(WebSocketSession session, UUID userId, String payload) {
        try {
            JsonNode frame = objectMapper.readTree(payload);
            String type = requiredText(frame, "type");

            return switch (type) {
                case "subscribe" -> handleSubscribe(session, userId, frame);
                case "unsubscribe" -> handleUnsubscribe(session);
                case "send" -> handleSend(session, userId, frame);
                case "send_dm" -> handleSendDirectMessage(session, userId, frame);
                default -> sendError(session, "invalid_frame", "Unsupported frame type: " + type);
            };
        } catch (ResponseStatusException exception) {
            return sendError(session, statusCodeToErrorCode(exception.getStatusCode().value()), exception.getReason());
        } catch (Exception exception) {
            return sendError(session, "invalid_frame", "Unable to process realtime frame");
        }
    }

    private Mono<Void> handleSubscribe(WebSocketSession session, UUID userId, JsonNode frame) {
        UUID channelId = UUID.fromString(requiredText(frame, "channelId"));
        RealtimeChannelScope channelScope = RealtimeChannelScope.valueOf(requiredText(frame, "channelScope"));

        return Mono.fromCallable(() -> {
                    subscriptionAuthorizer.requireSubscribeAccess(channelId, userId, channelScope);
                    subscriptionHub.subscribe(session, userId, channelId, channelScope);
                    return Map.<String, Object>of(
                            "type", "subscribed",
                            "channelId", channelId.toString(),
                            "channelScope", channelScope.name()
                    );
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(payload -> sendJson(session, payload))
                .onErrorResume(ResponseStatusException.class, exception ->
                        sendError(session, statusCodeToErrorCode(exception.getStatusCode().value()), exception.getReason()))
                .onErrorResume(exception -> sendError(session, "error", "Realtime request failed"));
    }

    private Mono<Void> handleUnsubscribe(WebSocketSession session) {
        subscriptionHub.unsubscribeAll(session);
        return sendJson(session, Map.of("type", "unsubscribed"));
    }

    private Mono<Void> handleSend(WebSocketSession session, UUID userId, JsonNode frame) {
        UUID channelId = UUID.fromString(requiredText(frame, "channelId"));
        RealtimeChannelScope channelScope = RealtimeChannelScope.valueOf(requiredText(frame, "channelScope"));
        String body = requiredText(frame, "body");

        return Mono.fromCallable(() -> {
                    subscriptionAuthorizer.requireSubscribeAccess(channelId, userId, channelScope);
                    return channelMessageClient.postMessage(channelId, userId, channelScope, body);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(message -> subscriptionHub.publishMessage(message, channelScope)
                        .onErrorResume(error -> Mono.empty()))
                .onErrorResume(ResponseStatusException.class, exception ->
                        sendError(session, statusCodeToErrorCode(exception.getStatusCode().value()), exception.getReason()))
                .onErrorResume(exception -> sendError(session, "error", "Realtime request failed"));
    }

    private Mono<Void> handleSendDirectMessage(WebSocketSession session, UUID userId, JsonNode frame) {
        UUID recipientUserId = UUID.fromString(requiredText(frame, "recipientUserId"));
        String body = requiredText(frame, "body");
        String clientMessageId = optionalText(frame, "clientMessageId");

        return Mono.defer(() -> socialRealtimeHub.sendDirectMessage(userId, recipientUserId, body, clientMessageId))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ResponseStatusException.class, exception ->
                        sendError(session, statusCodeToErrorCode(exception.getStatusCode().value()), exception.getReason()))
                .onErrorResume(exception -> sendError(session, "error", "Realtime request failed"));
    }

    private Mono<Void> sendInitialFriendPresence(WebSocketSession session, UUID userId) {
        return socialFriendsClient.listFriendUserIds(userId)
                .flatMap(friendUserIds -> {
                    Mono<Void> deliveries = Mono.empty();
                    for (UUID friendUserId : friendUserIds) {
                        if (presenceStore.isOnline(friendUserId)) {
                            deliveries = deliveries.then(sendJson(session, Map.of(
                                    "type", "presence_changed",
                                    "userId", friendUserId.toString(),
                                    "status", "online"
                            )));
                        }
                    }
                    return deliveries;
                });
    }

    private UUID authenticate(WebSocketSession session) {
        String userIdHeader = session.getHandshakeInfo().getHeaders().getFirst(AuthHeaders.USER_ID);
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                return UUID.fromString(userIdHeader);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        String accessToken = queryParam(session.getHandshakeInfo().getUri(), "access_token");
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }

        try {
            return jwtTokenService.parseUserId(AuthHeaders.BEARER_PREFIX + accessToken);
        } catch (InvalidJwtException exception) {
            return null;
        }
    }

    private Mono<Void> sendError(WebSocketSession session, String code, String message) {
        return sendJson(session, Map.of(
                "type", "error",
                "code", code,
                "message", message == null ? "Realtime request failed" : message
        ));
    }

    private Mono<Void> sendJson(WebSocketSession session, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return session.send(Mono.just(session.textMessage(json)));
        } catch (Exception exception) {
            return Mono.empty();
        }
    }

    private static String requiredText(JsonNode frame, String fieldName) {
        JsonNode value = frame.get(fieldName);
        if (value == null || value.asText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing field: " + fieldName);
        }
        return value.asText();
    }

    private static String optionalText(JsonNode frame, String fieldName) {
        JsonNode value = frame.get(fieldName);
        if (value == null || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }

    private static String statusCodeToErrorCode(int statusCode) {
        return switch (statusCode) {
            case 403 -> "forbidden";
            case 404 -> "not_found";
            case 400 -> "bad_request";
            default -> "error";
        };
    }

    private static String queryParam(URI uri, String name) {
        String query = uri.getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }

        for (String part : query.split("&")) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length == 2 && name.equals(keyValue[0])) {
                return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
            }
        }

        return null;
    }
}
