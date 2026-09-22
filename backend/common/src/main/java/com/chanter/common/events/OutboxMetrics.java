package com.chanter.common.events;

import com.chanter.common.recovery.OrdinaryOperation;
import com.chanter.common.telemetry.QueueMetricQuery;
import com.chanter.common.telemetry.QueueMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OrdinaryOperation
@ConditionalOnProperty(name = "chanter.telemetry.enabled", havingValue = "true")
public class OutboxMetrics {
    @Bean(initMethod = "start", destroyMethod = "close")
    QueueMetrics outboxQueueMetrics(DataSource source, MeterRegistry registry) {
        return new QueueMetrics(QueueMetrics.Queue.EVENTS, registry, QueueMetricQuery.source(source, """
                SELECT COUNT(CASE WHEN status IN ('PENDING','SENDING') THEN 1 END) pending,
                       COUNT(CASE WHEN status='FAILED' THEN 1 END) failed,
                       MIN(CASE WHEN status IN ('PENDING','SENDING') THEN created_at END) oldest
                FROM durable_outbox WHERE status IN ('PENDING','SENDING','FAILED')
                """), Clock.systemUTC());
    }
}
