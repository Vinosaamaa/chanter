package com.chanter.agent.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.chanter.common.auth.AuthHeaders;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class NativeSessionClientTest {
    @Test void privateHttpContractChecksIdentityAndNeverCachesRevokedAuthority() throws Exception {
        UUID user = UUID.randomUUID(), session = UUID.randomUUID();
        var status = new AtomicInteger(200); var calls = new AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/auth/session/introspect", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst(AuthHeaders.INTERNAL_SERVICE_TOKEN)).isEqualTo("test-service-token-316-at-least-32-bytes");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer synthetic-access-token");
            byte[] body = ("{\"userId\":\"" + user + "\",\"sessionId\":\"" + session + "\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var client = new NativeSessionClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-service-token-316-at-least-32-bytes", Clock.systemUTC());
            assertThat(client.requireActive("Bearer synthetic-access-token", user).sessionId()).isEqualTo(session);
            assertThatThrownBy(() -> client.requireActive("Bearer synthetic-access-token", UUID.randomUUID()))
                    .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
            status.set(401);
            assertThatThrownBy(() -> client.requireActive("Bearer synthetic-access-token", user))
                    .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
            assertThat(calls.get()).isEqualTo(3);
        } finally { server.stop(0); }
    }
}
