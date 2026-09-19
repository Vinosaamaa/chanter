package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;
import com.chanter.media.infra.HttpResourceIngestionClient;
import com.chanter.common.auth.AuthHeaders;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ResourceIngestionHttpTest {
    @Test void documentsAndUnsupportedFormatsReachTheAgentAndReturnExplicitOutcomes() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        UUID course = UUID.randomUUID(), resource = UUID.randomUUID();
        var body = new AtomicReference<String>();
        var token = new AtomicReference<String>();
        var response = new AtomicReference<>("{\"resourceId\":\"" + resource + "\",\"courseId\":\"" + course
                + "\",\"status\":\"OCR_REQUIRED\",\"signals\":[]}");
        server.createContext("/api/v1/internal/resource-chunks/ingest", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            token.set(exchange.getRequestHeaders().getFirst(AuthHeaders.INTERNAL_SERVICE_TOKEN));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var client = new HttpResourceIngestionClient("http://127.0.0.1:" + server.getAddress().getPort(), 2, 2, "test-internal-service-token-for-media");
            for (String file : java.util.List.of("scan.pdf", "notes.docx", "slides.pptx", "recording.mp4")) {
                assertThat(client.ingestAiApprovedResource(course, resource, file, new byte[]{1, 2, 3}).status()).isEqualTo("OCR_REQUIRED");
                assertThat(body.get()).contains(file, "AQID");
                assertThat(token.get()).isEqualTo("test-internal-service-token-for-media");
            }
            response.set("{}");
            assertThatThrownBy(() -> client.ingestAiApprovedResource(course, resource, "notes.docx", new byte[]{1}))
                    .isInstanceOf(IllegalStateException.class);
        } finally { server.stop(0); }
    }
}
