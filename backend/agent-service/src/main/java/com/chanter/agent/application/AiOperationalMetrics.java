package com.chanter.agent.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Operational observations only; database usage remains the accounting authority. */
@Component
public final class AiOperationalMetrics {
    private static final Set<String> OUTCOMES = Set.of("SUCCESS", "UNAVAILABLE", "RATE_LIMITED", "TIMED_OUT", "CANCELLED",
            "INVALID_RESPONSE", "REFUSED", "LIMIT_EXCEEDED", "REJECTED_EVIDENCE", "UNSUPPORTED", "UNKNOWN", "NOT_STARTED");
    private final MeterRegistry registry;
    private final boolean enabled;
    public AiOperationalMetrics(MeterRegistry registry, @Value("${chanter.telemetry.enabled:false}") boolean enabled) {
        this.registry = registry; this.enabled = enabled;
    }
    public void reserved() {
        onCommit(() -> registry.counter("chanter.ai.requests").increment());
    }
    public void settled(String outcome, boolean measured, boolean attempted, long latencyMs) {
        if (!OUTCOMES.contains(outcome)) return;
        onCommit(() -> {
            registry.counter("chanter.ai.settlements", "outcome", outcome, "usage", measured ? "measured" : "unmeasured").increment();
            if (attempted && !measured) registry.counter("chanter.ai.unmeasured_settlements").increment();
            // Native client execution has no measured server-side duration.
            if (latencyMs > 0) registry.timer("chanter.ai.duration", "outcome", outcome).record(latencyMs, TimeUnit.MILLISECONDS);
        });
    }
    private void onCommit(Runnable observation) {
        if (!enabled) return;
        Runnable safe = () -> {
            try { observation.run(); }
            catch (RuntimeException unavailable) { /* Telemetry cannot change a committed product result. */ }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { safe.run(); }
            });
        } else safe.run();
    }
}
