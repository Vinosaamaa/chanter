package com.chanter.telemetry;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.View;
import java.util.Map;

/** The agent instruments existing HTTP/JDBC clients; every exported span crosses this boundary. */
public final class TelemetryPrivacy implements AutoConfigurationCustomizerProvider {
    @Override public void customize(AutoConfigurationCustomizer builder) {
        builder.addSpanExporterCustomizer((exporter, config) -> new SafeSpanExporter(exporter));
        builder.addMetricExporterCustomizer((exporter, config) -> new SafeMetricExporter(exporter));
        builder.addMeterProviderCustomizer((provider, config) -> provider.registerView(
                InstrumentSelector.builder().setName("*").build(), View.builder()
                        .setAttributeFilter(SafeMetricExporter.ATTRIBUTE_NAMES).setCardinalityLimit(512).build()));
        builder.addPropertiesCustomizer(config -> Map.of(
                "otel.logs.exporter", "none", "otel.metrics.exemplar.filter", "always_off",
                "otel.propagators", "tracecontext",
                "otel.bsp.max.queue.size", "512", "otel.bsp.max.export.batch.size", "64",
                "otel.bsp.export.timeout", "2000", "otel.exporter.otlp.timeout", "2000",
                "otel.instrumentation.jdbc.statement-sanitizer.enabled", "true"));
    }
}
