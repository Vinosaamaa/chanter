package com.chanter.telemetry;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
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
    @Test void realAgentExportsCorrelatedHttpSpansThroughThePrivacyExtension() throws Exception {
        List<ExportTraceServiceRequest> received = Collections.synchronizedList(new ArrayList<>());
        var collector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        collector.createContext("/v1/traces", exchange -> {
            received.add(ExportTraceServiceRequest.parseFrom(exchange.getRequestBody()));
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        collector.start();
        try {
            runFixture("http://127.0.0.1:" + collector.getAddress().getPort() + "/v1/traces");
            assertFalse(received.isEmpty(), "The real agent must reach the receiver");
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
        var output = Files.createTempFile(Path.of("target"), "agent-fixture-", ".log");
        var java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        var builder = new ProcessBuilder(java.toString(), "-javaagent:" + System.getProperty("chanter.telemetry.agent"),
                "-cp", System.getProperty("java.class.path"), AgentFixture.class.getName()).redirectErrorStream(true).redirectOutput(output.toFile());
        var env = builder.environment();
        env.put("OTEL_JAVAAGENT_EXTENSIONS", Path.of("target/telemetry-0.1.0-SNAPSHOT.jar").toAbsolutePath().toString());
        env.put("OTEL_TRACES_EXPORTER", "otlp"); env.put("OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf");
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
