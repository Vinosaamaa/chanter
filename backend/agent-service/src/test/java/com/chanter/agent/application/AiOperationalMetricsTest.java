package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class AiOperationalMetricsTest {
    @Test void rollbackEmitsNothingAndNativeUnknownUsageDoesNotBecomeMeasuredZeroLatency() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        var registry = new SimpleMeterRegistry();
        var metrics = new AiOperationalMetrics(registry, true);
        try {
            tx.executeWithoutResult(status -> { metrics.reserved(); metrics.settled("SUCCESS", true, true, 10); status.setRollbackOnly(); });
            assertThat(registry.getMeters()).isEmpty();
            tx.executeWithoutResult(status -> { metrics.reserved(); metrics.settled("SUCCESS", false, true, 0); });
            assertThat(registry.get("chanter.ai.requests").counter().count()).isEqualTo(1);
            assertThat(registry.get("chanter.ai.unmeasured_settlements").counter().count()).isEqualTo(1);
            assertThat(registry.find("chanter.ai.duration").timer()).isNull();
            metrics.settled("private-canary", true, true, 1);
            assertThat(registry.getMeters()).hasSize(3);
        } finally { registry.close(); }
    }
    @Test void disabledOrBrokenTelemetryDoesNotChangeTheProductOperation() {
        var registry = mock(MeterRegistry.class);
        when(registry.counter("chanter.ai.requests")).thenThrow(new IllegalStateException("private-canary"));
        assertThatCode(() -> new AiOperationalMetrics(registry, true).reserved()).doesNotThrowAnyException();
        var disabled = new SimpleMeterRegistry();
        try {
            new AiOperationalMetrics(disabled, false).settled("TIMED_OUT", false, true, 20);
            assertThat(disabled.getMeters()).isEmpty();
        } finally { disabled.close(); }
    }
}
