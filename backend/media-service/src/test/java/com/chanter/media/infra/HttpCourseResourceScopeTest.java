package com.chanter.media.infra;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class HttpCourseResourceScopeTest {
    @Test void permanentScopeFailuresKeepTheirStatusAndOutagesRemainRetryable() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var code = new AtomicInteger(404);
        server.createContext("/api/v1/internal/course-scope/", exchange -> {
            exchange.sendResponseHeaders(code.get(), -1); exchange.close();
        });
        server.start();
        try {
            var client = new HttpCourseResourceAccessClient("http://127.0.0.1:" + server.getAddress().getPort(), 2, 2, "test-internal-service-token-for-media");
            for (int status : new int[]{403, 404, 503}) {
                code.set(status);
                assertThatThrownBy(() -> client.requireStudyServerId(UUID.randomUUID()))
                        .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode().value()).isEqualTo(status));
            }
        } finally { server.stop(0); }
    }
}
