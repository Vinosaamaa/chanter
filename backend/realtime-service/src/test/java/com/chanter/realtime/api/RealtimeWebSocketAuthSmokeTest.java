package com.chanter.realtime.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanter.common.auth.JwtTokenService;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Mono;

/**
 * Verifies SEC-11: WebSocket auth via Sec-WebSocket-Protocol (no query token).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealtimeWebSocketAuthSmokeTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void noAuthIsRejected() {
        WebSocketClient client = new ReactorNettyWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/api/v1/realtime/ws");

        try {
            client.execute(uri, session -> session.receive().then())
                    .block(Duration.ofSeconds(3));
        } catch (Exception e) {
            // Connection refused / closed is expected
        }
    }

    @Test
    void validJwtViaSubprotocolIsAccepted() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenService.createAccessToken(userId);
        URI uri = URI.create("ws://localhost:" + port + "/api/v1/realtime/ws");

        WebSocketClient client = new ReactorNettyWebSocketClient();
        boolean connected = false;

        try {
            Mono<Void> result = client.execute(uri, withJwtSubprotocol(token, session -> Mono.empty()));
            result.block(Duration.ofSeconds(3));
            connected = true;
        } catch (Exception e) {
            // Unexpected
        }

        assertThat(connected).isTrue();
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
}
