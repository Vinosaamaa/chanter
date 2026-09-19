package com.chanter.search.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanter.search.config.MediaServiceClientProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HttpMediaCatalogClientTest {
    @Test
    void onlyPublicAvailableStatusIsSearchable() throws Exception {
        UUID available = UUID.randomUUID();
        UUID course = UUID.randomUUID();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/courses/" + course + "/course-resources", exchange -> {
            byte[] body = ("{\"courseResources\":[{\"id\":\"" + available + "\",\"courseId\":\"" + course
                    + "\",\"title\":\"Available resource\",\"fileName\":\"lesson.pdf\",\"status\":\"AVAILABLE\"},"
                    + "{\"id\":\"" + UUID.randomUUID() + "\",\"courseId\":\"" + course
                    + "\",\"title\":\"Quarantined\",\"fileName\":\"pending.pdf\",\"status\":\"PROCESSING\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var client = new HttpMediaCatalogClient(new MediaServiceClientProperties(
                    "http://127.0.0.1:" + server.getAddress().getPort(), 2, 3), "test-internal-token-with-at-least-32-characters");
            assertThat(client.listCourseResources(course, UUID.randomUUID())).extracting(resource -> resource.id()).containsExactly(available);
        } finally { server.stop(0); }
    }
}
