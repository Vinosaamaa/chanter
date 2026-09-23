package com.chanter.auth.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.chanter.common.lifecycle.AccountExportProtocol;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ExportSourceClientDeadlineTest {
    @Test void continuousBodyDripCannotKeepAReadAliveBeyondTheWholeFetchDeadline() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var started = new CountDownLatch(4); var stopped = new CountDownLatch(4);
        var serverWorkers = Executors.newCachedThreadPool(); server.setExecutor(serverWorkers);
        server.createContext("/api/v1/internal/lifecycle/exports/", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 0); started.countDown();
                for (int count = 0; count < 240; count++) {
                    exchange.getResponseBody().write('x'); exchange.getResponseBody().flush();
                    try { Thread.sleep(25); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                }
            } catch (java.io.IOException disconnected) {
                // The test expects cancellation to reach the actual socket while the body is still arriving.
            } finally { stopped.countDown(); }
        });
        server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        var destinations = AccountExportProtocol.SOURCES.stream().filter(source -> !source.equals("auth"))
                .collect(java.util.stream.Collectors.toMap(source -> source, source -> base));
        try (var source = new ExportSourceClient(destinations, "fixture-export-private-service-token", new ObjectMapper(), null, Duration.ofSeconds(1));
                var requests = Executors.newFixedThreadPool(4)) {
            var manifest = new ExportSnapshotStore.Manifest(1, "community", UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(60), List.of());
            long began = System.nanoTime();
            var responses = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int index = 0; index < 4; index++) responses.add(requests.submit(() ->
                    assertThatThrownBy(() -> source.page(manifest, 0, 0)).isInstanceOf(java.io.IOException.class).hasMessage("EXPORT_SOURCE_TIMEOUT")));
            for (var response : responses) response.get(3, TimeUnit.SECONDS);
            assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofSeconds(3));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(stopped.await(2, TimeUnit.SECONDS)).isTrue();
        } finally { server.stop(0); serverWorkers.shutdownNow(); }
    }
}
