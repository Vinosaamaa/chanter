package com.chanter.realtime.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.realtime.domain.RealtimeChannelScope;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class HttpChannelSubscriptionAuthorizerTest {

    private static final String INTERNAL_SERVICE_TOKEN = "test-internal-service-token-for-realtime";

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> capturedUserId = new AtomicReference<>();
    private final AtomicReference<String> capturedInternalToken = new AtomicReference<>();
    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{}";

    @BeforeEach
    void setUp() throws IOException {
        capturedUserId.set(null);
        capturedInternalToken.set(null);
        capturedPath.set(null);
        responseStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedUserId.set(exchange.getRequestHeaders().getFirst(AuthHeaders.USER_ID));
            capturedInternalToken.set(
                    exchange.getRequestHeaders().getFirst(AuthHeaders.INTERNAL_SERVICE_TOKEN)
            );
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void requireSubscribeAccessSendsInternalServiceTokenAndUserId() {
        UUID channelId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        responseBody = """
                {
                  "channelId": "%s",
                  "canReadMessages": true,
                  "canPostMessages": true
                }
                """.formatted(channelId);
        HttpChannelSubscriptionAuthorizer authorizer =
                new HttpChannelSubscriptionAuthorizer(baseUrl, INTERNAL_SERVICE_TOKEN);

        assertThatCode(() -> authorizer.requireSubscribeAccess(
                channelId,
                userId,
                RealtimeChannelScope.COURSE
        )).doesNotThrowAnyException();

        assertThat(capturedPath.get()).isEqualTo(
                "/api/v1/course-channels/" + channelId + "/channel-message-access"
        );
        assertThat(capturedUserId.get()).isEqualTo(userId.toString());
        assertThat(capturedInternalToken.get()).isEqualTo(INTERNAL_SERVICE_TOKEN);
    }

    @Test
    void requireSubscribeAccessMapsUnauthorizedToBadGateway() {
        UUID channelId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        responseStatus = 401;
        responseBody = """
                {"timestamp":"2026-07-16T00:00:00Z","status":401,"error":"Unauthorized","path":"/api/v1/course-channels/%s/channel-message-access"}
                """.formatted(channelId);
        HttpChannelSubscriptionAuthorizer authorizer =
                new HttpChannelSubscriptionAuthorizer(baseUrl, INTERNAL_SERVICE_TOKEN);

        assertThatThrownBy(() -> authorizer.requireSubscribeAccess(
                channelId,
                userId,
                RealtimeChannelScope.COURSE
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
