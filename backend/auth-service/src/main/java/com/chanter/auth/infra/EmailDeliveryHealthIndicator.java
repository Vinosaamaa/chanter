package com.chanter.auth.infra;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("emailDeliveryHealthIndicator")
public class EmailDeliveryHealthIndicator implements HealthIndicator {

    private final JdbcEmailOutbox outbox;

    public EmailDeliveryHealthIndicator(JdbcEmailOutbox outbox) {
        this.outbox = outbox;
    }

    @Override
    public Health health() {
        try {
            var status = outbox.deliveryStatus();
            boolean stalled = status.retrying() > 0 || status.oldestPendingSeconds() > 60;
            return (stalled ? Health.down() : Health.up())
                    .withDetail("pending", status.pending())
                    .withDetail("retrying", status.retrying())
                    .withDetail("oldestPendingSeconds", status.oldestPendingSeconds()).build();
        } catch (RuntimeException exception) {
            return Health.down().withDetail("queue", "unavailable").build();
        }
    }
}
