package com.chanter.realtime.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanter.common.auth.JwtTokenService;
import com.chanter.realtime.domain.RealtimeChannelScope;
import com.chanter.realtime.infra.TestChannelSubscriptionAuthorizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealtimeWebSocketSmokeTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private TestChannelSubscriptionAuthorizer subscriptionAuthorizer;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        subscriptionAuthorizer.clear();
    }

    @Test
    void subscribeSendAndReceiveChannelMessage() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        subscriptionAuthorizer.grant(channelId, userId, RealtimeChannelScope.COURSE);

        String token = jwtTokenService.createAccessToken(userId);
        WebSocketClient client = new ReactorNettyWebSocketClient();
        AtomicReference<JsonNode> messageFrame = new AtomicReference<>();

        client.execute(
                websocketUri(token),
                session -> {
                    Flux<String> inbound = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .replay()
                            .autoConnect(1);

                    Mono<Void> waitForSubscribed = inbound
                            .filter(payload -> payload.contains("\"type\":\"subscribed\""))
                            .next()
                            .then();

                    Mono<Void> subscribe = session.send(Mono.just(session.textMessage(toJson(Map.of(
                            "type", "subscribe",
                            "channelId", channelId.toString(),
                            "channelScope", RealtimeChannelScope.COURSE.name()
                    )))));

                    Mono<Void> send = session.send(Mono.just(session.textMessage(toJson(Map.of(
                            "type", "send",
                            "channelId", channelId.toString(),
                            "channelScope", RealtimeChannelScope.COURSE.name(),
                            "body", "Hello #questions"
                    )))));

                    Mono<Void> waitForMessage = inbound
                            .filter(payload -> payload.contains("\"type\":\"message\""))
                            .next()
                            .doOnNext(payload -> {
                                try {
                                    messageFrame.set(objectMapper.readTree(payload));
                                } catch (Exception exception) {
                                    throw new IllegalStateException(exception);
                                }
                            })
                            .then();

                    return Mono.when(waitForSubscribed, subscribe).then(send).then(waitForMessage);
                }
        ).block(Duration.ofSeconds(5));

        assertThat(messageFrame.get()).isNotNull();
        assertThat(messageFrame.get().get("payload").get("body").asText()).isEqualTo("Hello #questions");
        assertThat(messageFrame.get().get("channelId").asText()).isEqualTo(channelId.toString());
    }

    @Test
    void unauthorizedSubscriptionReceivesErrorFrame() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String token = jwtTokenService.createAccessToken(userId);
        WebSocketClient client = new ReactorNettyWebSocketClient();
        AtomicReference<JsonNode> errorFrame = new AtomicReference<>();

        client.execute(
                websocketUri(token),
                session -> {
                    Flux<String> inbound = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .replay()
                            .autoConnect(1);

                    Mono<Void> waitForError = inbound
                            .filter(payload -> payload.contains("\"type\":\"error\""))
                            .next()
                            .doOnNext(payload -> {
                                try {
                                    errorFrame.set(objectMapper.readTree(payload));
                                } catch (Exception exception) {
                                    throw new IllegalStateException(exception);
                                }
                            })
                            .then();

                    Mono<Void> subscribe = session.send(Mono.just(session.textMessage(toJson(Map.of(
                            "type", "subscribe",
                            "channelId", channelId.toString(),
                            "channelScope", RealtimeChannelScope.COURSE.name()
                    )))));

                    return Mono.when(waitForError, subscribe).then();
                }
        ).block(Duration.ofSeconds(5));

        assertThat(errorFrame.get()).isNotNull();
        assertThat(errorFrame.get().get("code").asText()).isEqualTo("forbidden");
    }

    private URI websocketUri(String accessToken) {
        return URI.create("ws://localhost:" + port + "/api/v1/realtime/ws?access_token=" + accessToken);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
