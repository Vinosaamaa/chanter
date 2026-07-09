package com.chanter.realtime.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanter.common.auth.JwtTokenService;
import com.chanter.realtime.infra.InMemoryDirectMessageCallStore;
import com.chanter.realtime.infra.SocialGraph;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
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
class DirectMessageCallSignalingSmokeTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private SocialGraph socialGraph;

    @Autowired
    private InMemoryDirectMessageCallStore callStore;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        socialGraph.clear();
        callStore.clear();
    }

    @Test
    void friendsCanInviteAcceptAndReceiveMediaToken() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        socialGraph.befriend(userA, userB);

        String tokenA = jwtTokenService.createAccessToken(userA);
        String tokenB = jwtTokenService.createAccessToken(userB);
        WebSocketClient client = new ReactorNettyWebSocketClient();

        AtomicReference<JsonNode> calleeRinging = new AtomicReference<>();
        AtomicReference<JsonNode> acceptedOnCallee = new AtomicReference<>();
        CountDownLatch calleeReady = new CountDownLatch(1);
        AtomicReference<Throwable> calleeFailure = new AtomicReference<>();

        Thread calleeThread = new Thread(() -> {
            try {
                client.execute(
                        websocketUri(tokenB),
                        session -> {
                            Flux<String> inbound = session.receive()
                                    .map(WebSocketMessage::getPayloadAsText)
                                    .replay()
                                    .autoConnect(1);

                            Mono<Void> ready = inbound
                                    .filter(payload -> payload.contains("\"type\":\"social_subscribed\""))
                                    .next()
                                    .doOnSuccess(ignored -> calleeReady.countDown())
                                    .then();

                            Mono<Void> waitRinging = inbound
                                    .filter(payload -> payload.contains("\"type\":\"call_ringing\""))
                                    .next()
                                    .doOnNext(payload -> captureFrame(payload, calleeRinging))
                                    .then();

                            Mono<Void> acceptCall = waitRinging.then(Mono.defer(() -> {
                                String callId = calleeRinging.get().get("callId").asText();
                                return session.send(Mono.just(session.textMessage(toJson(Map.of(
                                        "type", "call_accept",
                                        "callId", callId
                                )))));
                            }));

                            Mono<Void> waitAccepted = inbound
                                    .filter(payload -> payload.contains("\"type\":\"call_accepted\""))
                                    .next()
                                    .doOnNext(payload -> captureFrame(payload, acceptedOnCallee))
                                    .then();

                            return ready.then(acceptCall).then(waitAccepted);
                        }
                ).block(Duration.ofSeconds(15));
            } catch (Throwable throwable) {
                calleeFailure.set(throwable);
            }
        });
        calleeThread.start();

        assertThat(calleeReady.await(5, TimeUnit.SECONDS)).isTrue();

        AtomicReference<JsonNode> callerAccepted = new AtomicReference<>();
        client.execute(
                websocketUri(tokenA),
                session -> {
                    Flux<String> inbound = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .replay()
                            .autoConnect(1);

                    Mono<Void> subscribed = inbound
                            .filter(payload -> payload.contains("\"type\":\"social_subscribed\""))
                            .next()
                            .then();

                    Mono<Void> invite = subscribed.then(session.send(Mono.just(session.textMessage(toJson(Map.of(
                            "type", "call_invite",
                            "calleeUserId", userB.toString()
                    ))))));

                    Mono<Void> waitAccepted = inbound
                            .filter(payload -> payload.contains("\"type\":\"call_accepted\""))
                            .next()
                            .doOnNext(payload -> captureFrame(payload, callerAccepted))
                            .then();

                    return invite.then(waitAccepted);
                }
        ).block(Duration.ofSeconds(15));

        calleeThread.join(15_000);
        if (calleeFailure.get() != null) {
            throw new AssertionError(calleeFailure.get());
        }

        assertThat(calleeRinging.get()).isNotNull();
        assertThat(calleeRinging.get().get("callerUserId").asText()).isEqualTo(userA.toString());
        assertThat(callerAccepted.get()).isNotNull();
        assertThat(acceptedOnCallee.get()).isNotNull();

        String callId = callerAccepted.get().get("callId").asText();
        var tokenResponse = org.springframework.web.reactive.function.client.WebClient.create()
                .post()
                .uri("http://localhost:" + port + "/api/v1/direct-message-calls/{callId}/media-token", callId)
                .header("X-User-Id", userA.toString())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(5));

        assertThat(tokenResponse).isNotNull();
        assertThat(tokenResponse.get("roomName").asText()).isEqualTo("dm-call-" + callId);

        client.execute(
                websocketUri(tokenA),
                session -> session.send(Mono.just(session.textMessage(toJson(Map.of(
                        "type", "call_end",
                        "callId", callId
                )))))
        ).block(Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        callStore.clear();
    }

    @Test
    void nonFriendCallInviteReceivesForbiddenError() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        String tokenA = jwtTokenService.createAccessToken(userA);
        WebSocketClient client = new ReactorNettyWebSocketClient();
        AtomicReference<JsonNode> errorFrame = new AtomicReference<>();

        client.execute(
                websocketUri(tokenA),
                session -> {
                    Flux<String> inbound = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .replay()
                            .autoConnect(1);

                    Mono<Void> subscribed = inbound
                            .filter(payload -> payload.contains("\"type\":\"social_subscribed\""))
                            .next()
                            .then();

                    Mono<Void> invite = subscribed.then(session.send(Mono.just(session.textMessage(toJson(Map.of(
                            "type", "call_invite",
                            "calleeUserId", userB.toString()
                    ))))));

                    Mono<Void> waitError = inbound
                            .filter(payload -> payload.contains("\"type\":\"error\""))
                            .next()
                            .doOnNext(payload -> captureFrame(payload, errorFrame))
                            .then();

                    return invite.then(waitError);
                }
        ).block(Duration.ofSeconds(10));

        assertThat(errorFrame.get()).isNotNull();
        assertThat(errorFrame.get().get("code").asText()).isEqualTo("forbidden");
    }

    private URI websocketUri(String accessToken) {
        return URI.create("ws://localhost:" + port + "/api/v1/realtime/ws?access_token=" + accessToken);
    }

    private void captureFrame(String payload, AtomicReference<JsonNode> target) {
        try {
            target.set(objectMapper.readTree(payload));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
