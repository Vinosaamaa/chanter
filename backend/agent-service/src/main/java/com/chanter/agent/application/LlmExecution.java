package com.chanter.agent.application;

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static com.chanter.agent.application.LlmProviderException.Outcome;

/** Owns the deadline and abort hooks for a single request, including blocking response-body reads. */
public final class LlmExecution implements AutoCloseable {
    private static final ScheduledThreadPoolExecutor DEADLINES = new ScheduledThreadPoolExecutor(1, r -> {
        Thread thread = new Thread(r, "ai-deadlines"); thread.setDaemon(true); return thread;
    });
    static { DEADLINES.setRemoveOnCancelPolicy(true); }
    private final AtomicReference<Outcome> stopped = new AtomicReference<>();
    private final CopyOnWriteArrayList<Runnable> aborts = new CopyOnWriteArrayList<>();
    private final ScheduledFuture<?> deadline;
    private CancellationHook parentHook;
    public LlmExecution(Duration timeout) {
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(120)) > 0)
            throw new IllegalArgumentException("Invalid AI deadline");
        deadline = DEADLINES.schedule(() -> stop(Outcome.TIMED_OUT), timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
    public LlmExecution child(Duration timeout) {
        check();
        LlmExecution child = new LlmExecution(timeout);
        child.parentHook = onCancel(() -> child.stop(stopped.get()));
        return child;
    }
    public CancellationHook onCancel(Runnable abort) {
        aborts.add(abort);
        if (stopped.get() != null) { abort.run(); aborts.remove(abort); }
        return () -> aborts.remove(abort);
    }
    public void cancel() { stop(Outcome.CANCELLED); }
    private void stop(Outcome reason) {
        if (stopped.compareAndSet(null, reason)) for (Runnable abort : aborts) {
            try { abort.run(); } catch (RuntimeException ignored) { /* cancellation is best effort */ }
        }
    }
    public void check() {
        if (Thread.currentThread().isInterrupted()) cancel();
        Outcome outcome = stopped.get();
        if (outcome != null) throw new LlmProviderException(outcome);
    }
    @Override public void close() {
        deadline.cancel(false);
        if (parentHook != null) parentHook.close();
        aborts.clear();
    }
    public interface CancellationHook extends AutoCloseable { @Override void close(); }
}
