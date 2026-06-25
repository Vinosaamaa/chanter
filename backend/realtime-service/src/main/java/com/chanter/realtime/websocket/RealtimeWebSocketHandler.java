package com.chanter.realtime.websocket;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InvalidJwtException;
import com.chanter.common.auth.JwtTokenService;
import com.chanter.realtime.application.ChannelMessageClient;
import com.chanter.realtime.application.ChannelSubscriptionAuthorizer;
import com.chanter.realtime.application.PersistedChannelMessage;
import com.chanter.realtime.domain.RealtimeChannelScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
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

    private final JwtTokenService jwtTokenService;
    private final ChannelSubscriptionAuthorizer subscriptionAuthorizer;
    private final ChannelMessageClient channelMessageClient;
    private final RealtimeSubscriptionHub subscriptionHub;
    private final ObjectMapper objectMapper;

    public RealtimeWebSocketHandler(
            JwtTokenService jwtTokenService,
            ChannelSubscriptionAuthorizer subscriptionAuthorizer,
            ChannelMessageClient channelMessageClient,
            RealtimeSubscriptionHub subscriptionHub,
            ObjectMapper objectMapper
    ) {
        this.jwtTokenService = jwtTokenService;
        this.subscriptionAuthorizer = subscriptionAuthorizer;
        this.channelMessageClient = channelMessageClient;
        this.subscriptionHub = subscriptionHub;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        UUID userId = authenticate(session);
        if (userId == null) {
            return session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Authentication required"));
        }

        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .concatMap(payload -> handleClientFrame(session, userId, payload))
                .then()
                .doFinally(signalType -> subscriptionHub.unsubscribeAll(session));
    }

    private Mono<Void> handleClientFrame(WebSocketSession session, UUID userId, String payload) {
        try {
            JsonNode frame = objectMapper.readTree(payload);
            String type = requiredText(frame, "type");

            return switch (type) {
                case "subscribe" -> handleSubscribe(session, userId, frame);
                case "unsubscribe" -> handleUnsubscribe(session);
                case "send" -> handleSend(session, userId, frame);
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
                        sendError(session, statusCodeToErrorCode(exception.getStatusCode().value()), exception.getReason()));
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
                .flatMap(message -> subscriptionHub.publishMessage(message, channelScope))
                .onErrorResume(ResponseStatusException.class, exception ->
                        sendError(session, statusCodeToErrorCode(exception.getStatusCode().value()), exception.getReason()));
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
