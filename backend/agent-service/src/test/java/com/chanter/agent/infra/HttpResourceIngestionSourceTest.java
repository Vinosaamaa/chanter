package com.chanter.agent.infra;

import static org.assertj.core.api.Assertions.*;
import com.chanter.agent.application.ResourceIngestionJobs;
import com.chanter.common.auth.AuthHeaders;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpResourceIngestionSourceTest {
    @Test void workerDownloadIsAuthenticatedVersionScopedAndBounded() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var body = new AtomicReference<>(new byte[]{1, 2, 3});
        var code = new AtomicInteger(200);
        var query = new AtomicReference<String>();
        var token = new AtomicReference<String>();
        server.createContext("/api/v1/internal/resource-ingestion/", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            token.set(exchange.getRequestHeaders().getFirst(AuthHeaders.INTERNAL_SERVICE_TOKEN));
            exchange.sendResponseHeaders(code.get(), body.get().length);
            try { exchange.getResponseBody().write(body.get()); } catch (java.io.IOException clientRejected) { }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var client = new HttpResourceIngestionSource("http://127.0.0.1:" + server.getAddress().getPort(), "test-internal-service-token-for-agent");
            var job = new ResourceIngestionJobs.Job(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, "a".repeat(64), "guide.txt", 1, UUID.randomUUID());
            assertThat(client.download(job)).containsExactly(1, 2, 3);
            assertThat(query.get()).contains("courseId=" + job.courseId(), "sha256=" + job.sourceSha256());
            assertThat(token.get()).isEqualTo("test-internal-service-token-for-agent");
            code.set(404);
            assertThatThrownBy(() -> client.download(job)).isInstanceOf(IllegalStateException.class);
            code.set(200); body.set(new byte[10 * 1024 * 1024 + 1]);
            assertThatThrownBy(() -> client.download(job)).isInstanceOf(IllegalStateException.class);
        } finally { server.stop(0); }
    }
}
