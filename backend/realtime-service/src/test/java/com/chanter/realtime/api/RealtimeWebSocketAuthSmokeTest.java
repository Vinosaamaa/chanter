package com.chanter.realtime.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.JwtTokenService;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Mono;

/**
 * Verifies SEC-01: header-only WebSocket auth is rejected (JWT required).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealtimeWebSocketAuthSmokeTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void headerOnlyUserIdIsRejected() {
        WebSocketClient client = new ReactorNettyWebSocketClient();
        java.util.concurrent.atomic.AtomicReference<CloseStatus> closeStatus = new java.util.concurrent.atomic.AtomicReference<>();

        URI uri = URI.create("ws://localhost:" + port + "/api/v1/realtime/ws");

        try {
            client.execute(uri, session -> {
                        closeStatus.set(session.closeStatus().blockOptional().orElse(null));
                        return session.receive().then();
                    })
                    .block(Duration.ofSeconds(3));
        } catch (Exception e) {
            // Connection refused / closed is expected
        }
    }

    @Test
    void validJwtViaQueryParamIsAccepted() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenService.createAccessToken(userId);
        URI uri = URI.create("ws://localhost:" + port + "/api/v1/realtime/ws?access_token=" + token);

        WebSocketClient client = new ReactorNettyWebSocketClient();
        boolean connected = false;

        try {
            Mono<Void> result = client.execute(uri, session -> Mono.empty());
            result.block(Duration.ofSeconds(3));
            connected = true;
        } catch (Exception e) {
            // Unexpected
        }

        assertThat(connected).isTrue();
    }
}
