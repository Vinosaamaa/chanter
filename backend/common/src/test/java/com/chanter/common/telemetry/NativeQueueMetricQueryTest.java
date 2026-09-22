package com.chanter.common.telemetry;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfSystemProperty(named = "chanter.telemetry.jdbc", matches = "jdbc:postgresql:.+")
class NativeQueueMetricQueryTest {
    @Test void postgresStopsTheTimedOutStatementRatherThanLeavingBackgroundDatabaseWork() {
        String url = System.getProperty("chanter.telemetry.jdbc");
        var source = new DriverManagerDataSource(url, "metric_test", "metric_test");
        var properties = new java.util.Properties(); properties.setProperty("ApplicationName", "chanter-metric-query");
        source.setConnectionProperties(properties);
        var slow = QueueMetricQuery.source(source, "SELECT (SELECT 0 FROM pg_sleep(20)) pending, 0 failed, NULL::timestamptz oldest");
        assertTimeoutPreemptively(Duration.ofSeconds(6), () -> assertThatThrownBy(slow::get).isInstanceOf(org.springframework.dao.DataAccessException.class));
        var observer = new JdbcTemplate(new DriverManagerDataSource(url, "metric_test", "metric_test"));
        observer.setQueryTimeout(2);
        assertThat(observer.queryForObject("SELECT COUNT(*) FROM pg_stat_activity WHERE application_name='chanter-metric-query' AND state='active'", Long.class)).isZero();
        var restored = QueueMetricQuery.source(source, "SELECT 0 pending, 0 failed, NULL::timestamptz oldest").get();
        assertThat(restored.pending()).isZero();
        assertThat(restored.failed()).isZero();
    }
}
