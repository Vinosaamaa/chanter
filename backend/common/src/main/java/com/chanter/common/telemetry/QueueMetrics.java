package com.chanter.common.telemetry;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/** Source queries run on one independent sampler; export callbacks only inspect bounded cached state. */
public final class QueueMetrics implements AutoCloseable {
    private static final Duration STALE_AFTER = Duration.ofSeconds(90);
    public enum Queue { EVENTS, EMAIL, RESOURCES }
    public record Snapshot(long pending, long failed, Instant oldest) {
        public Snapshot {
            if (pending < 0 || failed < 0 || (pending > 0 && oldest == null))
                throw new IllegalArgumentException("Invalid queue measurement");
        }
    }
    private record Observation(Snapshot snapshot, Instant sampledAt, boolean successful) { }
    private final Supplier<Snapshot> source;
    private final Clock clock;
    private final MeterRegistry registry;
    private final List<Meter> meters = new ArrayList<>();
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final ScheduledThreadPoolExecutor scheduler;
    private volatile Observation observation;

    public QueueMetrics(Queue queue, MeterRegistry registry, Supplier<Snapshot> source, Clock clock) {
        this.source = source; this.clock = clock; this.registry = registry;
        String prefix = "chanter." + queue.name().toLowerCase(java.util.Locale.ROOT) + ".";
        scheduler = new ScheduledThreadPoolExecutor(1, task -> {
            var thread = new Thread(task, prefix + "sampler"); thread.setDaemon(true); return thread;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        gauge(prefix + "pending", "1", value -> value.snapshotValue(snapshot -> snapshot.pending()));
        gauge(prefix + "failed", "1", value -> value.snapshotValue(snapshot -> snapshot.failed()));
        gauge(prefix + "oldest.age", "s", value -> value.snapshotValue(snapshot -> snapshot.pending() == 0 ? 0 : value.age(snapshot.oldest())));
        gauge(prefix + "collection.healthy", "1", value -> {
            var current = value.observation; return value.fresh(current) && current.successful() ? 1 : 0;
        });
        gauge(prefix + "sample.age", "s", value -> {
            var current = value.observation; return current == null || current.sampledAt() == null ? Double.NaN : value.age(current.sampledAt());
        });
    }

    public void start() {
        if (!closed.get() && started.compareAndSet(false, true)) scheduler.scheduleWithFixedDelay(this::sample, 0, 30, TimeUnit.SECONDS);
    }
    public void sample() {
        if (closed.get() || !inFlight.compareAndSet(false, true)) return;
        try {
            Snapshot next = java.util.Objects.requireNonNull(source.get());
            if (!closed.get()) observation = new Observation(next, clock.instant(), true);
        } catch (RuntimeException unavailable) {
            var previous = observation;
            observation = previous == null ? new Observation(null, null, false)
                    : new Observation(previous.snapshot(), previous.sampledAt(), false);
        } finally { inFlight.set(false); }
    }
    private void gauge(String name, String unit, ToDoubleFunction<QueueMetrics> reader) {
        meters.add(Gauge.builder(name, this, reader).baseUnit(unit).register(registry));
    }
    private double snapshotValue(ToDoubleFunction<Snapshot> reader) {
        var current = observation;
        return fresh(current) ? reader.applyAsDouble(current.snapshot()) : Double.NaN;
    }
    private boolean fresh(Observation value) {
        return value != null && value.snapshot() != null && value.sampledAt() != null
                && !clock.instant().isAfter(value.sampledAt().plus(STALE_AFTER));
    }
    private double age(Instant timestamp) { return Math.max(0, Duration.between(timestamp, clock.instant()).toMillis() / 1000.0); }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        scheduler.shutdownNow(); meters.forEach(registry::remove);
    }
}
