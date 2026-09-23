package com.chanter.common.lifecycle;

import com.chanter.common.events.DurableEvent;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Admission and deadlines only. The durable outbox remains the sole retry owner. */
public final class ExportSourceExecution implements AutoCloseable {
    public static final Duration CAPTURE_LIMIT = Duration.ofSeconds(30);
    public static final Duration READ_LIMIT = Duration.ofSeconds(10);
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();
    private final PlatformTransactionManager transactions;
    private final Duration captureLimit;
    private final Duration readLimit;
    private final Duration deliveryWait;
    private final Semaphore waiters = new Semaphore(4);
    private final Semaphore readers = new Semaphore(4);
    private final ScheduledThreadPoolExecutor deadlines = new ScheduledThreadPoolExecutor(1,
            Thread.ofPlatform().daemon().name("export-deadline").factory());
    private Task<Void> active;
    private boolean closed;

    public ExportSourceExecution(PlatformTransactionManager transactions) {
        this(transactions, CAPTURE_LIMIT, READ_LIMIT, Duration.ofSeconds(3));
    }

    ExportSourceExecution(PlatformTransactionManager transactions, Duration captureLimit, Duration readLimit, Duration deliveryWait) {
        this.transactions = transactions; this.captureLimit = captureLimit;
        this.readLimit = readLimit; this.deliveryWait = deliveryWait;
        deadlines.setRemoveOnCancelPolicy(true);
    }

    public void deliver(DurableEvent event, Runnable work) {
        if (!waiters.tryAcquire()) throw unavailable();
        try {
            Task<Void> task;
            synchronized (this) {
                if (closed) throw unavailable();
                if (active != null) {
                    if (!active.event.equals(event)) throw unavailable();
                    task = active;
                } else {
                    task = new Task<>(event); active = task;
                    start(task, captureLimit, () -> { work.run(); return null; }, () -> {
                        synchronized (this) { active = null; }
                    });
                }
            }
            await(task, deliveryWait);
        } finally { waiters.release(); }
    }

    public <T> T read(Callable<T> work) {
        synchronized (this) { if (closed) throw unavailable(); }
        if (!readers.tryAcquire()) throw unavailable();
        var task = new Task<T>(null);
        start(task, readLimit, work, readers::release);
        return await(task, readLimit.plusSeconds(2));
    }

    private <T> void start(Task<T> task, Duration limit, Callable<T> work, Runnable release) {
        task.thread = Thread.ofVirtual().name("export-source").unstarted(() -> {
            T value = null;
            Throwable failure = null;
            DEADLINE.set(System.nanoTime() + limit.toNanos());
            java.util.concurrent.ScheduledFuture<?> alarm = null;
            try {
                alarm = deadlines.schedule(Thread.currentThread()::interrupt, limit.toNanos(), TimeUnit.NANOSECONDS);
                var tx = new TransactionTemplate(transactions);
                tx.setTimeout(Math.max(1, (int) Math.ceil(limit.toMillis() / 1000.0)));
                value = tx.execute(status -> {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void beforeCommit(boolean readOnly) { check(); }
                    });
                    check();
                    try { return work.call(); }
                    catch (RuntimeException | Error exception) { throw exception; }
                    catch (Exception exception) { throw new ExportSnapshotStore.ExportFailure("EXPORT_SOURCE_UNAVAILABLE", exception); }
                });
            } catch (Throwable exception) { failure = exception; }
            finally {
                if (alarm != null) alarm.cancel(false);
                DEADLINE.remove();
                // execute() has returned: rollback/commit, connection release and lock release precede admission.
                release.run();
            }
            if (failure == null) task.result.complete(value);
            else task.result.completeExceptionally(failure);
        });
        task.thread.start();
    }

    static void check() {
        Long deadline = DEADLINE.get();
        if (Thread.currentThread().isInterrupted() || (deadline != null && System.nanoTime() - deadline >= 0))
            throw unavailable();
    }

    private static <T> T await(Task<T> task, Duration wait) {
        try { return task.result.get(wait.toNanos(), TimeUnit.NANOSECONDS); }
        catch (TimeoutException failure) { throw unavailable(); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw unavailable();
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "EXPORT_SOURCE_BUSY_OR_EXPIRED");
    }

    @Override public synchronized void close() {
        closed = true;
        if (active != null) active.thread.interrupt();
        // Already scheduled read deadlines still run; no deadline is discarded during shutdown.
        deadlines.shutdown();
    }

    private static final class Task<T> {
        final DurableEvent event;
        final CompletableFuture<T> result = new CompletableFuture<>();
        Thread thread;
        Task(DurableEvent event) { this.event = event; }
    }
}
