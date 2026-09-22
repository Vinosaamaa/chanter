package com.chanter.telemetry;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.Data;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** SDK views remove private dimensions before aggregation; the exporter rejects any unsafe residual point. */
public final class SafeMetricExporter implements MetricExporter {
    private static final Set<String> NAMES = Set.of(
            "http.server.request.duration", "http.client.request.duration", "db.client.operation.duration",
            "db.client.connection.count", "db.client.connection.pending_requests", "db.client.connection.timeouts",
            "jvm.memory.used", "jvm.memory.committed", "jvm.memory.limit", "jvm.memory.used_after_last_gc",
            "jvm.gc.duration", "jvm.thread.count", "jvm.class.loaded", "jvm.class.unloaded", "jvm.class.count",
            "jvm.cpu.time", "jvm.cpu.count", "jvm.cpu.recent_utilization", "process.cpu.time", "process.cpu.utilization",
            "chanter.events.pending", "chanter.events.failed", "chanter.events.oldest.age",
            "chanter.events.collection.healthy", "chanter.events.sample.age",
            "chanter.email.pending", "chanter.email.failed", "chanter.email.oldest.age",
            "chanter.email.collection.healthy", "chanter.email.sample.age",
            "chanter.auth.email.delivery", "chanter.gateway.admission",
            "chanter.resources.pending", "chanter.resources.failed", "chanter.resources.oldest.age",
            "chanter.resources.collection.healthy", "chanter.resources.sample.age",
            "chanter.ai.requests", "chanter.ai.duration", "chanter.ai.settlements", "chanter.ai.unmeasured_settlements",
            "chanter.ai.unknown_usage", "chanter.realtime.connections");
    private static final Set<String> UNITS = Set.of("", "1", "s", "ms", "By", "{thread}", "{class}", "{connection}", "{request}");
    // Preserve bounded runtime dimensions: collapsing asynchronous gauges from
    // distinct memory pools/thread states would keep one value rather than sum.
    private static final Map<String, Set<String>> DIMENSIONS = Map.of(
            "outcome", Set.of("accepted", "expired", "retry", "limited", "unavailable", "bounded-recovery",
                    "SUCCESS", "UNAVAILABLE", "RATE_LIMITED", "TIMED_OUT", "CANCELLED", "INVALID_RESPONSE", "REFUSED",
                    "LIMIT_EXCEEDED", "REJECTED_EVIDENCE", "UNSUPPORTED", "UNKNOWN", "NOT_STARTED"),
            "usage", Set.of("measured", "unmeasured"),
            "operation", Set.of("READ", "WRITE", "AUTH", "REGISTRATION", "RECOVERY", "LOGOUT", "RECONNECT",
                    "AI", "UPLOAD", "DOWNLOAD", "SEARCH", "MESSAGE", "SENSITIVE"),
            "jvm.memory.type", Set.of("heap", "non_heap"),
            "jvm.memory.pool.name", Set.of("CodeHeap 'non-nmethods'", "CodeHeap 'profiled nmethods'",
                    "CodeHeap 'non-profiled nmethods'", "Code Cache", "Metaspace", "Compressed Class Space",
                    "G1 Eden Space", "G1 Survivor Space", "G1 Old Gen", "Eden Space", "Survivor Space", "Tenured Gen",
                    "PS Eden Space", "PS Survivor Space", "PS Old Gen", "ZHeap", "ZGC Young Generation", "ZGC Old Generation"),
            "jvm.thread.state", Set.of("new", "runnable", "blocked", "waiting", "timed_waiting", "terminated"),
            "jvm.gc.name", Set.of("G1 Young Generation", "G1 Old Generation", "Copy", "MarkSweepCompact",
                    "PS Scavenge", "PS MarkSweep", "ZGC Cycles", "ZGC Pauses", "ZGC Minor Cycles", "ZGC Minor Pauses",
                    "ZGC Major Cycles", "ZGC Major Pauses"),
            "jvm.gc.action", Set.of("end of minor GC", "end of major GC"),
            "db.client.connection.state", Set.of("idle", "used"));
    static final Set<String> ATTRIBUTE_NAMES = java.util.stream.Stream.concat(DIMENSIONS.keySet().stream(),
            java.util.stream.Stream.of("http.response.status_code", "jvm.thread.daemon")).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private final MetricExporter delegate;
    public SafeMetricExporter(MetricExporter delegate) { this.delegate = delegate; }
    @Override public AggregationTemporality getAggregationTemporality(InstrumentType type) { return delegate.getAggregationTemporality(type); }
    @Override public CompletableResultCode export(Collection<MetricData> metrics) {
        return delegate.export(metrics.stream().filter(SafeMetricExporter::safe).map(SafeMetricExporter::sanitize).toList());
    }
    @Override public CompletableResultCode flush() { return delegate.flush(); }
    @Override public CompletableResultCode shutdown() { return delegate.shutdown(); }
    private static boolean safe(MetricData metric) {
        // The Java agent owns runtime/HTTP metrics. Bridged Boot meters may use
        // the same names with different dimensions and must not double-count.
        if (metric.getInstrumentationScopeInfo().getName().equals("io.opentelemetry.micrometer-1.5")
                && !metric.getName().startsWith("chanter.")) return false;
        if (!NAMES.contains(metric.getName()) || !UNITS.contains(metric.getUnit()) || metric.getData().getPoints().size() > 512) return false;
        return metric.getData().getPoints().stream().allMatch(point -> point.getExemplars().isEmpty()
                && point.getAttributes().asMap().entrySet().stream().allMatch(entry -> safeAttribute(entry.getKey().getKey(), entry.getValue())));
    }
    private static boolean safeAttribute(String name, Object value) {
        if (name.equals("http.response.status_code")) return value instanceof Long status && status >= 100 && status <= 599;
        if (name.equals("jvm.thread.daemon")) return value instanceof Boolean;
        return value instanceof String text && DIMENSIONS.getOrDefault(name, Set.of()).contains(text);
    }
    private static MetricData sanitize(MetricData source) {
        return new MetricData() {
            @Override public Resource getResource() { return SafeSpanExporter.safeResource(source.getResource()); }
            @Override public InstrumentationScopeInfo getInstrumentationScopeInfo() { return InstrumentationScopeInfo.create("chanter.private-metrics"); }
            @Override public String getName() { return source.getName(); }
            @Override public String getDescription() { return ""; }
            @Override public String getUnit() { return source.getUnit(); }
            @Override public MetricDataType getType() { return source.getType(); }
            @Override public Data<?> getData() { return source.getData(); }
        };
    }
}
