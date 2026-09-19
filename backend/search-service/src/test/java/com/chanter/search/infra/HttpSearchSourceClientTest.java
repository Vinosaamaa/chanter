package com.chanter.search.infra;

import static org.assertj.core.api.Assertions.*;
import com.chanter.search.config.*;
import com.chanter.search.domain.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class HttpSearchSourceClientTest {
    @Test void currentSourceContentReplacesSnapshotAndDeniedOrUnavailableSourcesFailClosed() throws Exception {
        UUID source = UUID.randomUUID(), studyServer = UUID.randomUUID();
        var status = new AtomicInteger(200);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/study-servers/" + studyServer + "/announcements/" + source, exchange -> {
            byte[] body = ("{\"id\":\"" + source + "\",\"title\":\"Current title\",\"body\":\"Current text\",\"status\":\"PUBLISHED\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var client = new HttpSearchSourceClient(new CommunityServiceClientProperties(base, 2, 3),
                    new MessageServiceClientProperties(base, 2, 3), "test-internal-token-with-at-least-32-characters");
            var hit = new SearchHit(SearchDocumentType.ANNOUNCEMENT, null, "Community", source, "Old title", "Old text");
            var current = client.currentVisibleHit(hit, studyServer, UUID.randomUUID()).orElseThrow();
            assertThat(current.title()).isEqualTo("Current title");
            assertThat(current.snippet()).isEqualTo("Current text");
            for (int code : new int[] {403, 404}) { status.set(code); assertThat(client.currentVisibleHit(hit, studyServer, UUID.randomUUID())).isEmpty(); }
            for (int code : new int[] {401, 503}) {
                status.set(code);
                assertThatThrownBy(() -> client.currentVisibleHit(hit, studyServer, UUID.randomUUID()))
                        .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode().value()).isEqualTo(503));
            }
        } finally { server.stop(0); }
    }
}
