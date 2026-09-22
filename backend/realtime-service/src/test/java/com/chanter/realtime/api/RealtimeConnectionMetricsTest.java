package com.chanter.realtime.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanter.common.auth.JwtTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "chanter.telemetry.enabled=true")
@ActiveProfiles("test")
class RealtimeConnectionMetricsTest {
    @LocalServerPort int port;
    @Autowired MeterRegistry registry;
    @Autowired JwtTokenService tokens;

    @Test void realAuthenticatedConnectionIsCountedAndRemovedAfterSocketCloses() throws Exception {
        var gauge = registry.get("chanter.realtime.connections").gauge();
        assertThat(gauge.value()).isZero();
        var observed = new AtomicBoolean();
        String token = tokens.createAccessToken(UUID.randomUUID());
        new ReactorNettyWebSocketClient().execute(URI.create("ws://127.0.0.1:" + port + "/api/v1/realtime/ws"),
                new WebSocketHandler() {
                    @Override public List<String> getSubProtocols() { return List.of("chanter-jwt", token); }
                    @Override public Mono<Void> handle(WebSocketSession session) {
                        return session.receive().filter(frame -> frame.getPayloadAsText().contains("social_subscribed"))
                                .doOnNext(frame -> {
                                    assertThat(gauge.value()).isEqualTo(1);
                                    assertThat(gauge.getId().getTags()).isEmpty();
                                    observed.set(true);
                                }).next().then();
                    }
                }).block(Duration.ofSeconds(10));
        assertThat(observed).isTrue();
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (gauge.value() != 0 && System.nanoTime() < deadline) Thread.sleep(20);
        assertThat(gauge.value()).isZero();
    }
}
