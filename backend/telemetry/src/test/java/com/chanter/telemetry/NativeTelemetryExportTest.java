package com.chanter.telemetry;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.trace.v1.Span;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "chanter.telemetry.agent", matches = ".+")
class NativeTelemetryExportTest {
    @Test void realSpringApplicationExportsBoundedBusinessMetricsWithoutPrivateLabels() throws Exception {
        List<ExportMetricsServiceRequest> received = Collections.synchronizedList(new ArrayList<>());
        var collector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        collector.createContext("/v1/metrics", exchange -> {
            received.add(ExportMetricsServiceRequest.parseFrom(exchange.getRequestBody()));
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        collector.createContext("/v1/traces", exchange -> { exchange.getRequestBody().readAllBytes(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        collector.start();
        try {
            runFixture("http://127.0.0.1:" + collector.getAddress().getPort() + "/v1/traces", ApplicationMetricFixture.class);
            synchronized (received) {
                assertFalse(received.isEmpty(), "A real Boot registry must reach the configured agent exporter");
                assertTrue(received.stream().noneMatch(request -> request.toString().contains("private-canary")));
                var metrics = received.stream().flatMap(request -> request.getResourceMetricsList().stream())
                        .flatMap(resource -> resource.getScopeMetricsList().stream()).flatMap(scope -> scope.getMetricsList().stream()).toList();
                var deliveries = metrics.stream().filter(metric -> metric.getName().equals("chanter.auth.email.delivery")).toList();
                assertFalse(deliveries.isEmpty(), "Existing email delivery counters must survive private export");
                assertTrue(deliveries.stream().anyMatch(metric -> metric.getSum().getDataPointsCount() == 2
                        && metric.getSum().getDataPointsList().stream().anyMatch(point -> point.getAsDouble() == 600)
                        && metric.getSum().getDataPointsList().stream().anyMatch(point -> point.getAsDouble() == 3)),
                        "All private account dimensions must collapse while accepted and retry outcomes remain distinct");
                assertTrue(metrics.stream().anyMatch(metric -> metric.getName().equals("chanter.gateway.admission")
                        && metric.getSum().getDataPointsList().stream().anyMatch(point -> point.getAsDouble() == 2
                        && point.getAttributesList().stream().anyMatch(attribute -> attribute.getKey().equals("operation")
                        && attribute.getValue().getStringValue().equals("AI")))));
            }
        } finally { collector.stop(0); }
    }

    @Test void realAgentExportsCorrelatedHttpSpansThroughThePrivacyExtension() throws Exception {
        List<ExportTraceServiceRequest> received = Collections.synchronizedList(new ArrayList<>());
        List<ExportMetricsServiceRequest> metrics = Collections.synchronizedList(new ArrayList<>());
        var collector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        collector.createContext("/v1/traces", exchange -> {
            received.add(ExportTraceServiceRequest.parseFrom(exchange.getRequestBody()));
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        collector.createContext("/v1/metrics", exchange -> {
            metrics.add(ExportMetricsServiceRequest.parseFrom(exchange.getRequestBody()));
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        collector.start();
        try {
            runFixture("http://127.0.0.1:" + collector.getAddress().getPort() + "/v1/traces");
            assertFalse(received.isEmpty(), "The real agent must reach the receiver");
            assertFalse(metrics.isEmpty(), "The real agent must export sanitized metrics");
            synchronized (metrics) {
                assertTrue(metrics.stream().noneMatch(request -> request.toString().contains("private-canary")),
                        "Metric names, descriptions, resources, scopes and dimensions must exclude the canary");
                var durations = metrics.stream().flatMap(request -> request.getResourceMetricsList().stream())
                        .flatMap(resource -> resource.getScopeMetricsList().stream()).flatMap(scope -> scope.getMetricsList().stream())
                        .filter(metric -> metric.getName().equals("chanter.ai.duration")).toList();
                assertFalse(durations.isEmpty(), "Known application instruments must survive the privacy boundary");
                assertTrue(durations.stream().anyMatch(metric -> metric.getHistogram().getDataPointsCount() == 1
                        && metric.getHistogram().getDataPoints(0).getCount() == 600),
                        "Private dimensions must be removed before aggregation, preserving all 600 observations");
                assertTrue(durations.stream().allMatch(metric -> metric.getHistogram().getDataPointsList().stream()
                        .allMatch(point -> point.getExemplarsCount() == 0)), "Exemplars can contain private dimensions");
            }
            List<Span> spans = new ArrayList<>();
            synchronized (received) {
                for (var request : received) {
                    assertFalse(request.toString().contains("private-canary"), "Canary leaked in decoded OTLP");
                    for (var resource : request.getResourceSpansList()) {
                        assertTrue(resource.getResource().getAttributesList().stream().anyMatch(attribute ->
                                attribute.getKey().equals("service.version") && attribute.getValue().getStringValue().equals("a".repeat(40))));
                        resource.getScopeSpansList().forEach(scope -> spans.addAll(scope.getSpansList()));
                    }
                }
            }
            var client = spans.stream().filter(span -> span.getKind() == Span.SpanKind.SPAN_KIND_CLIENT).findFirst().orElseThrow();
            assertTrue(spans.stream().anyMatch(span -> span.getKind() == Span.SpanKind.SPAN_KIND_SERVER
                    && span.getTraceId().equals(client.getTraceId()) && span.getParentSpanId().equals(client.getSpanId())),
                    "Client and server must share propagated trace context");
        } finally { collector.stop(0); }
    }

    @Test void receiverOutageDoesNotBreakTheApplicationOrBlockShutdown() throws Exception {
        var unused = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
        int port = unused.getLocalPort(); unused.close();
        assertTimeoutPreemptively(Duration.ofSeconds(25), () -> runFixture("http://127.0.0.1:" + port + "/v1/traces"));
    }

    private static void runFixture(String endpoint) throws Exception {
        runFixture(endpoint, AgentFixture.class);
    }

    private static void runFixture(String endpoint, Class<?> fixture) throws Exception {
        var output = Files.createTempFile(Path.of("target"), "agent-fixture-", ".log");
        var java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        var builder = new ProcessBuilder(java.toString(), "-javaagent:" + System.getProperty("chanter.telemetry.agent"),
                "-cp", System.getProperty("java.class.path"), fixture.getName()).redirectErrorStream(true).redirectOutput(output.toFile());
        var env = builder.environment();
        env.put("OTEL_JAVAAGENT_EXTENSIONS", Path.of("target/telemetry-0.1.0-SNAPSHOT.jar").toAbsolutePath().toString());
        env.put("OTEL_TRACES_EXPORTER", "otlp"); env.put("OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf");
        env.put("OTEL_METRICS_EXPORTER", "otlp");
        env.put("OTEL_INSTRUMENTATION_MICROMETER_ENABLED", "true");
        env.put("OTEL_EXPORTER_OTLP_METRICS_ENDPOINT", endpoint.replace("/v1/traces", "/v1/metrics"));
        env.put("OTEL_METRIC_EXPORT_INTERVAL", "60000");
        env.put("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", endpoint); env.put("OTEL_TRACES_SAMPLER", "always_on");
        env.put("OTEL_BSP_SCHEDULE_DELAY", "100"); env.put("OTEL_SERVICE_NAME", "agent-service");
        env.put("OTEL_RESOURCE_ATTRIBUTES", "service.version=" + "a".repeat(40) + ",deployment.environment.name=test,private=private-canary");
        var process = builder.start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Agent process did not stop within the bounded deadline");
            assertEquals(0, process.exitValue(), () -> "Agent fixture failed; inspect " + output.getFileName());
        } finally { if (process.isAlive()) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS); }
    }
}
