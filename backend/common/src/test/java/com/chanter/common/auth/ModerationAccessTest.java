package com.chanter.common.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ModerationAccessTest {
    @Test void currentStatusIsCheckedEveryTimeAndOutageFailsClosed() throws Exception {
        AtomicInteger status = new AtomicInteger(200);
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/moderation/access", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst(AuthHeaders.INTERNAL_SERVICE_TOKEN)).isEqualTo("moderation-test-token");
            byte[] body = (status.get() == 200 ? "{\"allowed\":true}" : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var access = new ModerationAccess(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),
                    "moderation-test-token", new ObjectMapper());
            UUID user = UUID.randomUUID();
            access.requireAccount(user);
            status.set(403);
            assertThatThrownBy(() -> access.requireAccount(user)).isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException)error).getStatusCode().value()).isEqualTo(403));
            status.set(503);
            assertThatThrownBy(() -> access.requireAccount(user)).isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException)error).getStatusCode().value()).isEqualTo(503));
            assertThat(calls).hasValue(3);
        } finally { server.stop(0); }
    }

    @Test void malformedSuccessCannotGrantAccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/moderation/access", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var access = new ModerationAccess(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),
                    "moderation-test-token", new ObjectMapper());
            assertThatThrownBy(() -> access.requireAccount(UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        } finally { server.stop(0); }
    }
}
