package com.chanter.common.telemetry;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QueueMetricsTest {
    @Test void samplingFailureAndStalenessNeverBecomeAHealthyEmptyQueueOrTriggerQueriesDuringExport() {
        var clock = new MutableClock();
        var calls = new AtomicInteger();
        var registry = new SimpleMeterRegistry();
        try (var metrics = new QueueMetrics(QueueMetrics.Queue.EVENTS, registry, () -> {
                 if (calls.incrementAndGet() == 2) throw new IllegalStateException("PRIVATE_DATABASE_CANARY");
                 return new QueueMetrics.Snapshot(4, 2, clock.instant().minusSeconds(60));
             }, clock)) {
            assertThat(value(registry, "collection.healthy")).isZero();
            assertThat(value(registry, "pending")).isNaN();
            assertThat(calls.get()).isZero();
            metrics.sample();
            assertThat(value(registry, "pending")).isEqualTo(4);
            assertThat(value(registry, "failed")).isEqualTo(2);
            assertThat(value(registry, "oldest.age")).isEqualTo(60);
            assertThat(value(registry, "collection.healthy")).isEqualTo(1);
            assertThat(calls.get()).isEqualTo(1);
            clock.now = clock.now.plusSeconds(30);
            metrics.sample();
            assertThat(value(registry, "collection.healthy")).isZero();
            assertThat(value(registry, "pending")).isEqualTo(4);
            assertThat(value(registry, "sample.age")).isEqualTo(30);
            clock.now = clock.now.plusSeconds(61);
            assertThat(value(registry, "pending")).isNaN();
            assertThat(value(registry, "failed")).isNaN();
            assertThat(value(registry, "collection.healthy")).isZero();
            assertThat(calls.get()).isEqualTo(2);
            metrics.sample();
            assertThat(value(registry, "collection.healthy")).isEqualTo(1);
            assertThat(value(registry, "sample.age")).isZero();
        } finally { registry.close(); }
    }

    @Test void inconsistentOrNegativeSnapshotsCannotAdvertiseSuccess() {
        assertThatThrownBy(() -> new QueueMetrics.Snapshot(-1, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueueMetrics.Snapshot(1, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueueMetrics.Snapshot(0, -1, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static double value(SimpleMeterRegistry registry, String suffix) {
        return registry.get("chanter.events." + suffix).gauge().value();
    }
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-09-19T00:00:00Z");
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
