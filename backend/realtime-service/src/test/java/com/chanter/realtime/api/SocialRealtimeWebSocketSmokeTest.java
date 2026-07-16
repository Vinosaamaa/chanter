package com.chanter.realtime.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanter.common.auth.JwtTokenService;
import com.chanter.realtime.infra.InMemoryDirectMessageCallStore;
import com.chanter.realtime.infra.SocialGraph;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Order(1)
class SocialRealtimeWebSocketSmokeTest {

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
    void friendPresenceAndDirectMessagesFanOutOverWebSocket() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        socialGraph.befriend(userA, userB);

        String tokenA = jwtTokenService.createAccessToken(userA);
        String tokenB = jwtTokenService.createAccessToken(userB);
        WebSocketClient client = new ReactorNettyWebSocketClient();
        AtomicReference<JsonNode> presenceFrame = new AtomicReference<>();
        AtomicReference<JsonNode> dmFrame = new AtomicReference<>();
        CountDownLatch listenerReady = new CountDownLatch(1);
        CountDownLatch presenceSeen = new CountDownLatch(1);
        CountDownLatch dmSeen = new CountDownLatch(1);
        AtomicReference<Throwable> listenerFailure = new AtomicReference<>();

        Thread listenerThread = new Thread(() -> {
            try {
                client.execute(
                        websocketUri(),
                        withJwtSubprotocol(tokenB, session -> session.receive()
                                .map(WebSocketMessage::getPayloadAsText)
                                .doOnNext(payload -> {
                                    if (payload.contains("\"type\":\"social_subscribed\"")) {
                                        listenerReady.countDown();
                                    }
                                    if (payload.contains("\"type\":\"presence_changed\"")
                                            && payload.contains(userA.toString())
                                            && presenceFrame.get() == null) {
                                        captureFrame(payload, presenceFrame);
                                        presenceSeen.countDown();
                                    }
                                    if (payload.contains("\"type\":\"dm_message\"")
                                            && dmFrame.get() == null) {
                                        captureFrame(payload, dmFrame);
                                        dmSeen.countDown();
                                    }
                                })
                                // Keep the socket open until both fan-out frames arrive (or timeout).
                                .takeUntil(ignored -> presenceFrame.get() != null && dmFrame.get() != null)
                                .then())
                ).block(Duration.ofSeconds(45));
            } catch (Throwable throwable) {
                listenerFailure.set(throwable);
            }
        });
        listenerThread.start();

        assertThat(listenerReady.await(10, TimeUnit.SECONDS)).isTrue();
        if (listenerFailure.get() != null) {
            throw new AssertionError(listenerFailure.get());
        }

        client.execute(
                websocketUri(),
                withJwtSubprotocol(tokenA, session -> {
                    Flux<String> inbound = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .replay()
                            .autoConnect(0);

                    Mono<Void> waitForSubscribed = inbound
                            .filter(payload -> payload.contains("\"type\":\"social_subscribed\""))
                            .next()
                            .then();

                    Mono<Void> sendDm = session.send(Mono.just(session.textMessage(toJson(Map.of(
                            "type", "send_dm",
                            "recipientUserId", userB.toString(),
                            "body", "Want to study together?"
                    )))));

                    // Hold A's session open until B observes presence + DM, so disconnect
                    // offline fan-out cannot race ahead of the assertions.
                    return waitForSubscribed
                            .then(sendDm)
                            .then(Mono.fromRunnable(() -> {
                                try {
                                    if (!presenceSeen.await(15, TimeUnit.SECONDS)
                                            || !dmSeen.await(15, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException(
                                                "Timed out waiting for presence/DM fan-out on listener"
                                        );
                                    }
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(exception);
                                }
                            }));
                })
        ).block(Duration.ofSeconds(40));

        listenerThread.join(10_000);
        if (listenerFailure.get() != null) {
            throw new AssertionError(listenerFailure.get());
        }

        assertThat(presenceFrame.get()).isNotNull();
        assertThat(presenceFrame.get().get("userId").asText()).isEqualTo(userA.toString());
        assertThat(presenceFrame.get().get("status").asText()).isEqualTo("online");

        assertThat(dmFrame.get()).isNotNull();
        assertThat(dmFrame.get().get("payload").get("body").asText()).isEqualTo("Want to study together?");
        assertThat(dmFrame.get().get("payload").get("senderUserId").asText()).isEqualTo(userA.toString());
    }

    @Test
    void nonFriendDirectMessageReceivesForbiddenError() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        String tokenA = jwtTokenService.createAccessToken(userA);
        WebSocketClient client = new ReactorNettyWebSocketClient();
        AtomicReference<JsonNode> errorFrame = new AtomicReference<>();

        client.execute(
                websocketUri(),
                withJwtSubprotocol(tokenA, session -> {
                    Flux<String> inbound = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .replay()
                            .autoConnect(1);

                    Mono<Void> waitForSubscribed = inbound
                            .filter(payload -> payload.contains("\"type\":\"social_subscribed\""))
                            .next()
                            .then();

                    Mono<Void> sendDm = session.send(Mono.just(session.textMessage(toJson(Map.of(
                            "type", "send_dm",
                            "recipientUserId", userB.toString(),
                            "body", "Hello stranger"
                    )))));

                    Mono<Void> waitForError = inbound
                            .filter(payload -> payload.contains("\"type\":\"error\""))
                            .next()
                            .doOnNext(payload -> captureFrame(payload, errorFrame))
                            .then();

                    return waitForSubscribed.then(sendDm).then(waitForError);
                })
        ).block(Duration.ofSeconds(10));

        assertThat(errorFrame.get()).isNotNull();
        assertThat(errorFrame.get().get("code").asText()).isEqualTo("forbidden");
    }

    private URI websocketUri() {
        return URI.create("ws://localhost:" + port + "/api/v1/realtime/ws");
    }

    private static WebSocketHandler withJwtSubprotocol(String token, WebSocketHandler delegate) {
        return new WebSocketHandler() {
            @Override
            public List<String> getSubProtocols() {
                return List.of("chanter-jwt", token);
            }

            @Override
            public Mono<Void> handle(WebSocketSession session) {
                return delegate.handle(session);
            }
        };
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
