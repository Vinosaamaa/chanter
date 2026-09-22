package com.chanter.media.application;

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
public class ResourceQueueMetrics {
    @Bean(initMethod = "start", destroyMethod = "close")
    QueueMetrics resourceQueueMetrics(DataSource source, MeterRegistry registry) {
        // Retry/claim transitions update updated_at. Age measures pending-row
        // inactivity, not total wait since upload or first ingestion request.
        return new QueueMetrics(QueueMetrics.Queue.RESOURCES, registry, QueueMetricQuery.source(source, """
                SELECT COUNT(CASE WHEN pending_work THEN 1 END) pending,
                       COUNT(CASE WHEN failed_work THEN 1 END) failed,
                       MIN(CASE WHEN pending_work THEN updated_at END) oldest
                FROM (
                    SELECT updated_at,
                        (state IN ('STAGING','QUARANTINED','SCANNING','DELETE_PENDING')
                            OR (state IN ('SCAN_FAILED','REJECTED') AND byte_reservation=TRUE)
                            OR (state='AVAILABLE' AND ingestion_status IN ('PENDING','PROCESSING'))) pending_work,
                        (state='SCAN_FAILED' OR (state='AVAILABLE' AND ingestion_status='FAILED')) failed_work
                    FROM course_resources
                    WHERE state IN ('STAGING','QUARANTINED','SCANNING','DELETE_PENDING','SCAN_FAILED','REJECTED','AVAILABLE')
                ) work
                """), Clock.systemUTC());
    }
}
