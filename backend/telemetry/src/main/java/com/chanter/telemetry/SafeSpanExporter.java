package com.chanter.telemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Allowlisted export, not best-effort redaction of arbitrary user or provider text. */
public final class SafeSpanExporter implements SpanExporter {
    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final Set<String> DATABASES = Set.of("postgresql", "redis", "h2");
    private static final Set<String> OPERATIONS = Set.of("SELECT", "INSERT", "UPDATE", "DELETE", "COMMIT", "ROLLBACK", "CONNECT");
    private static final Set<String> SERVICES = Set.of("auth-service", "notification-service", "community-service", "message-service",
            "media-service", "agent-service", "analytics-service", "search-service", "realtime-service", "gateway-service");
    private final SpanExporter delegate;

    public SafeSpanExporter(SpanExporter delegate) { this.delegate = delegate; }

    @Override public CompletableResultCode export(Collection<SpanData> spans) {
        return delegate.export(spans.stream().map(SafeSpanExporter::sanitize).toList());
    }
    @Override public CompletableResultCode flush() { return delegate.flush(); }
    @Override public CompletableResultCode shutdown() { return delegate.shutdown(); }

    static SpanData sanitize(SpanData source) {
        var attributes = Attributes.builder();
        String method = source.getAttributes().get(AttributeKey.stringKey("http.request.method"));
        if (METHODS.contains(method == null ? "" : method)) attributes.put("http.request.method", method);
        Long status = source.getAttributes().get(AttributeKey.longKey("http.response.status_code"));
        if (status != null && status >= 100 && status <= 599) attributes.put("http.response.status_code", status);
        String database = source.getAttributes().get(AttributeKey.stringKey("db.system.name"));
        if (DATABASES.contains(database == null ? "" : database)) attributes.put("db.system.name", database);
        String operation = source.getAttributes().get(AttributeKey.stringKey("db.operation.name"));
        if (OPERATIONS.contains(operation == null ? "" : operation)) attributes.put("db.operation.name", operation);
        var safe = attributes.build();
        var resource = Attributes.builder();
        String service = source.getResource().getAttribute(AttributeKey.stringKey("service.name"));
        resource.put("service.name", SERVICES.contains(service == null ? "" : service) ? service : "unknown-service");
        String release = source.getResource().getAttribute(AttributeKey.stringKey("service.version"));
        if (release != null && release.matches("[a-f0-9]{40}")) resource.put("service.version", release);
        String environment = source.getResource().getAttribute(AttributeKey.stringKey("deployment.environment.name"));
        if (Set.of("staging", "production", "test").contains(environment == null ? "" : environment)) {
            resource.put("deployment.environment.name", environment);
        }
        Resource safeResource = Resource.create(resource.build());
        String name = safe.get(AttributeKey.stringKey("http.request.method")) != null ? "HTTP " + method
                : safe.get(AttributeKey.stringKey("db.operation.name")) != null ? "DB " + operation : source.getKind().name();
        return new DelegatingSpanData(source) {
            @Override public String getName() { return name; }
            @Override public Attributes getAttributes() { return safe; }
            @Override public int getTotalAttributeCount() { return safe.size(); }
            @Override public Resource getResource() { return safeResource; }
            @Override public StatusData getStatus() { return StatusData.create(source.getStatus().getStatusCode(), ""); }
            @Override public List<EventData> getEvents() { return List.of(); }
            @Override public int getTotalRecordedEvents() { return 0; }
            @Override public List<LinkData> getLinks() { return List.of(); }
            @Override public int getTotalRecordedLinks() { return 0; }
            @Override public SpanContext getSpanContext() { return withoutState(source.getSpanContext()); }
            @Override public SpanContext getParentSpanContext() { return withoutState(source.getParentSpanContext()); }
            @Override public InstrumentationScopeInfo getInstrumentationScopeInfo() { return InstrumentationScopeInfo.create("chanter.private-tracing"); }
        };
    }
    private static SpanContext withoutState(SpanContext context) {
        return SpanContext.create(context.getTraceId(), context.getSpanId(), context.getTraceFlags(), TraceState.getDefault());
    }
}
