package com.chanter.telemetry;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import java.util.Map;

/** The agent instruments existing HTTP/JDBC clients; every exported span crosses this boundary. */
public final class TelemetryPrivacy implements AutoConfigurationCustomizerProvider {
    @Override public void customize(AutoConfigurationCustomizer builder) {
        builder.addSpanExporterCustomizer((exporter, config) -> new SafeSpanExporter(exporter));
        builder.addPropertiesCustomizer(config -> Map.of(
                "otel.logs.exporter", "none", "otel.metrics.exporter", "none",
                "otel.propagators", "tracecontext",
                "otel.bsp.max.queue.size", "512", "otel.bsp.max.export.batch.size", "64",
                "otel.bsp.export.timeout", "2000", "otel.exporter.otlp.timeout", "2000",
                "otel.instrumentation.jdbc.statement-sanitizer.enabled", "true"));
    }
}
