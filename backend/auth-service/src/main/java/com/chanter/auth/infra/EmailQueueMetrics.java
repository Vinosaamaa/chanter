package com.chanter.auth.infra;

import com.chanter.common.telemetry.QueueMetricQuery;
import com.chanter.common.telemetry.QueueMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "chanter.telemetry.enabled", havingValue = "true")
public class EmailQueueMetrics {
    @Bean(name = "emailQueueSampler", initMethod = "start", destroyMethod = "close")
    QueueMetrics emailQueueMetrics(DataSource source, MeterRegistry registry) {
        // Expired rows retain content-free metadata for seven days. This is a
        // current retained failure count, not a monotonically increasing counter.
        return new QueueMetrics(QueueMetrics.Queue.EMAIL, registry, QueueMetricQuery.source(source, """
                SELECT COUNT(CASE WHEN status='PENDING' THEN 1 END) pending,
                       COUNT(CASE WHEN status='EXPIRED' THEN 1 END) failed,
                       MIN(CASE WHEN status='PENDING' THEN created_at END) oldest
                FROM auth_email_outbox WHERE status IN ('PENDING','EXPIRED')
                """), Clock.systemUTC());
    }
}
