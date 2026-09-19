package com.chanter.telemetry;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Synthetic process used by the real Java-agent export test. */
public class AgentFixture {
    public static void main(String[] args) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("private-canary".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var span = GlobalOpenTelemetry.getTracer("private-canary").spanBuilder("private-canary").startSpan();
            try (var ignored = span.makeCurrent(); var client = HttpClient.newHttpClient()) {
                span.setAttribute("db.query.text", "private-canary");
                span.recordException(new IllegalStateException("private-canary"));
                span.setStatus(StatusCode.ERROR, "private-canary");
                var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                                + "/private-canary?secret=private-canary"))
                        .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer private-canary").build();
                if (client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() != 200) throw new IllegalStateException();
            } finally { span.end(); }
        } finally { server.stop(0); }
    }
}
