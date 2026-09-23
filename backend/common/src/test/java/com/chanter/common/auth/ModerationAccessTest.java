package com.chanter.common.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ModerationAccessTest {
    @Test void liveSessionIntrospectionRejectsRevocationAndMismatchedIdentity() throws Exception {
        UUID user=UUID.randomUUID(),session=UUID.randomUUID();
        var reply=new AtomicReference<>("{\"userId\":\""+user+"\",\"sessionId\":\""+session+"\",\"expiresAt\":\""+java.time.Instant.now().plusSeconds(60)+"\"}");
        var status=new AtomicInteger(200);
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/v1/auth/session/introspect",exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst(AuthHeaders.AUTHORIZATION)).isEqualTo("Bearer session-test");
            assertThat(exchange.getRequestHeaders().getFirst(AuthHeaders.INTERNAL_SERVICE_TOKEN)).isEqualTo("test-token");
            byte[] body=reply.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(),body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var access=new ModerationAccess(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"test-token",new ObjectMapper());
            access.requireSession("Bearer session-test",user);
            assertThatThrownBy(() -> access.requireSession("Bearer session-test",UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
            status.set(401);
            assertThatThrownBy(() -> access.requireSession("Bearer session-test",user)).isInstanceOf(ResponseStatusException.class);
            status.set(410);
            assertThatThrownBy(() -> access.requireSession("Bearer session-test",user)).isInstanceOfSatisfying(ResponseStatusException.class,
                    error -> assertThat(error.getStatusCode().value()).isEqualTo(403));
            status.set(503);
            assertThatThrownBy(() -> access.requireSession("Bearer session-test",user)).isInstanceOfSatisfying(ResponseStatusException.class,
                    error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
            status.set(200); reply.set("{}");
            assertThatThrownBy(() -> access.requireSession("Bearer session-test",user)).isInstanceOf(ResponseStatusException.class);
        } finally { server.stop(0); }
    }
    @Test void aBatchFiltersOnlyNamedRestrictedSourcesAndRejectsAnUnrelatedReply() throws Exception {
        var visible=new ModerationAccess.Target("RESOURCE",UUID.randomUUID());
        var restricted=new ModerationAccess.Target("RESOURCE",UUID.randomUUID());
        ObjectMapper mapper=new ObjectMapper();
        AtomicReference<String> payload=new AtomicReference<>(mapper.writeValueAsString(
                java.util.Map.of("allowed",true,"restricted",List.of(restricted))));
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/v1/moderation/sources",exchange -> {
            byte[] body=payload.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var access=new ModerationAccess(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"test-token",mapper);
            assertThat(access.allowedSources(UUID.randomUUID(),List.of(visible,restricted))).containsExactly(visible);
            payload.set(mapper.writeValueAsString(java.util.Map.of("allowed",true,"restricted",
                    List.of(new ModerationAccess.Target("RESOURCE",UUID.randomUUID())))));
            assertThatThrownBy(() -> access.allowedSources(UUID.randomUUID(),List.of(visible,restricted)))
                    .isInstanceOf(ResponseStatusException.class);
        } finally { server.stop(0); }
    }

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
            status.set(410);
            assertThatThrownBy(() -> access.requireAccount(user)).isInstanceOfSatisfying(ResponseStatusException.class,
                    error -> assertThat(error.getStatusCode().value()).isEqualTo(403));
            assertThat(calls).hasValue(4);
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
