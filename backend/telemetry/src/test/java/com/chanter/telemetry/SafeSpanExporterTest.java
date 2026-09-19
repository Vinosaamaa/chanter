package com.chanter.telemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SafeSpanExporterTest {
    @Test void privateContentCannotLeaveThroughSpanNamesAttributesEventsResourcesLinksOrTraceState() {
        var exported = InMemorySpanExporter.create();
        try (var provider = SdkTracerProvider.builder().setResource(Resource.create(Attributes.builder()
                .put("service.name", "agent-service").put("service.version", "a".repeat(40))
                .put("deployment.environment.name", "production").put("process.command_args", "secret-canary").build()))
                .addSpanProcessor(SimpleSpanProcessor.create(new SafeSpanExporter(exported))).build()) {
            var parent = SpanContext.createFromRemoteParent("1".repeat(32), "2".repeat(16), TraceFlags.getSampled(),
                    TraceState.builder().put("vendor", "secret-canary").build());
            var span = provider.get("private-library-name").spanBuilder("GET /secret-canary")
                    .setSpanKind(SpanKind.SERVER).setParent(Context.root().with(Span.wrap(parent)))
                    .addLink(parent, Attributes.of(AttributeKey.stringKey("secret"), "secret-canary")).startSpan();
            span.setAttribute("http.request.method", "GET");
            span.setAttribute("http.response.status_code", 500);
            span.setAttribute("url.full", "https://example.test/secret-canary?token=secret-canary");
            span.setAttribute("db.query.text", "SELECT secret-canary");
            span.setAttribute("http.route", "/secret-canary");
            span.setAttribute("authorization", "secret-canary");
            span.recordException(new RuntimeException("secret-canary"));
            span.setStatus(StatusCode.ERROR, "secret-canary");
            span.end();
            var result = exported.getFinishedSpanItems().getFirst();
            assertEquals("HTTP GET", result.getName());
            assertEquals(2, result.getAttributes().size());
            assertEquals(3, result.getResource().getAttributes().size());
            assertFalse(result.getAttributes().toString().contains("secret-canary"));
            assertFalse(result.getResource().getAttributes().toString().contains("secret-canary"));
            assertEquals(List.of(), result.getEvents());
            assertEquals(List.of(), result.getLinks());
            assertEquals("", result.getStatus().getDescription());
            assertEquals(StatusCode.ERROR, result.getStatus().getStatusCode());
            assertEquals(parent.getTraceId(), result.getTraceId());
            assertEquals(parent.getSpanId(), result.getParentSpanId());
            assertTrue(result.getParentSpanContext().getTraceState().isEmpty());
            assertTrue(result.getSpanContext().getTraceState().isEmpty());
            assertEquals("chanter.private-tracing", result.getInstrumentationScopeInfo().getName());
        }
    }

    @Test void unexpectedValuesInOtherwiseAllowedFieldsAreDropped() {
        var exported = InMemorySpanExporter.create();
        try (var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(new SafeSpanExporter(exported))).build()) {
            var span = provider.get("test").spanBuilder("secret-canary").startSpan();
            span.setAttribute("http.request.method", "secret-canary");
            span.setAttribute("http.response.status_code", 999);
            span.setAttribute("db.system.name", "secret-canary");
            span.setAttribute("db.operation.name", "SELECT secret-canary");
            span.end();
            assertEquals("INTERNAL", exported.getFinishedSpanItems().getFirst().getName());
            assertTrue(exported.getFinishedSpanItems().getFirst().getAttributes().isEmpty());
        }
    }
}
