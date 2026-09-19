package com.chanter.telemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SafeMetricExporterTest {
    @Test void micrometerOnlyContributesBusinessMetricsSoRuntimeGaugesAreNotDuplicated() {
        var reader = InMemoryMetricReader.create();
        var destination = InMemoryMetricExporter.create();
        try (var provider = SdkMeterProvider.builder().registerMetricReader(reader).build()) {
            var bridge = provider.get("io.opentelemetry.micrometer-1.5");
            bridge.gaugeBuilder("jvm.memory.used").setUnit("By").buildWithCallback(gauge -> gauge.record(123));
            bridge.counterBuilder("chanter.auth.email.delivery").build().add(1, Attributes.builder().put("outcome", "accepted").build());
            new SafeMetricExporter(destination).export(reader.collectAllMetrics()).join(1, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(java.util.List.of("chanter.auth.email.delivery"), destination.getFinishedMetricItems().stream().map(metric -> metric.getName()).toList());
        }
    }

    @Test void unsafeResidualDimensionsNamesAndUnitsAreRejectedWithoutErasingRuntimePoolMeaning() {
        var reader = InMemoryMetricReader.create();
        var destination = InMemoryMetricExporter.create();
        try (var provider = SdkMeterProvider.builder().registerMetricReader(reader)
                .setResource(Resource.create(Attributes.builder().put("service.name", "agent-service")
                        .put("service.version", "a".repeat(40)).put("private", "private-canary").build())).build()) {
            var meter = provider.get("private-canary");
            meter.counterBuilder("private-canary").build().add(1);
            meter.counterBuilder("chanter.ai.requests").build().add(1, Attributes.builder().put("account", "private-canary").build());
            meter.histogramBuilder("chanter.ai.duration").setUnit("private-canary").build().record(1);
            meter.gaugeBuilder("jvm.memory.used").ofLongs().setUnit("By").setDescription("private-canary").buildWithCallback(gauge -> {
                gauge.record(100, Attributes.builder().put("jvm.memory.type", "heap").put("jvm.memory.pool.name", "G1 Eden Space").build());
                gauge.record(200, Attributes.builder().put("jvm.memory.type", "heap").put("jvm.memory.pool.name", "G1 Old Gen").build());
            });
            new SafeMetricExporter(destination).export(reader.collectAllMetrics()).join(1, java.util.concurrent.TimeUnit.SECONDS);
            var output = destination.getFinishedMetricItems();
            assertEquals(1, output.size());
            var metric = output.getFirst();
            assertEquals("jvm.memory.used", metric.getName());
            assertEquals(2, metric.getLongGaugeData().getPoints().size());
            assertEquals(300, metric.getLongGaugeData().getPoints().stream().mapToLong(point -> point.getValue()).sum());
            assertEquals("", metric.getDescription());
            assertEquals("chanter.private-metrics", metric.getInstrumentationScopeInfo().getName());
            assertFalse(metric.getResource().toString().contains("private-canary"));
        }
    }

    @Test void invalidValuesInsideAllowedDimensionsCannotLeaveTheExporter() {
        var reader = InMemoryMetricReader.create();
        var destination = InMemoryMetricExporter.create();
        try (var provider = SdkMeterProvider.builder().registerMetricReader(reader).build()) {
            var meter = provider.get("fixture");
            meter.counterBuilder("chanter.ai.requests").build().add(1,
                    Attributes.builder().put("http.response.status_code", "private-canary").build());
            meter.counterBuilder("chanter.auth.email.delivery").build().add(1,
                    Attributes.builder().put("outcome", "private-canary").build());
            meter.counterBuilder("chanter.gateway.admission").build().add(1,
                    Attributes.builder().put("operation", "private-canary").build());
            meter.gaugeBuilder("jvm.memory.used").setUnit("By").buildWithCallback(gauge -> gauge.record(100,
                    Attributes.builder().put("jvm.memory.pool.name", "private-canary").build()));
            new SafeMetricExporter(destination).export(reader.collectAllMetrics()).join(1, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(destination.getFinishedMetricItems().isEmpty());
        }
    }
}
