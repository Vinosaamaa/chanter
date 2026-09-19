package com.chanter.common.telemetry;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class QueueMetricQueryTest {
    @Test void fixedAggregateReadsHaveStatementAndNetworkDeadlinesAndReturnTheirConnection() {
        var source = new TrackingSource();
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE work (status VARCHAR(20), created_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("INSERT INTO work VALUES ('PENDING', TIMESTAMP WITH TIME ZONE '2026-09-19 00:00:00Z'), ('FAILED', CURRENT_TIMESTAMP)");
        var query = QueueMetricQuery.source(source, "SELECT COUNT(CASE WHEN status='PENDING' THEN 1 END) pending, COUNT(CASE WHEN status='FAILED' THEN 1 END) failed, MIN(CASE WHEN status='PENDING' THEN created_at END) oldest FROM work");
        var result = query.get();
        assertThat(source.active.get()).isZero();
        assertThat(source.timeouts).endsWith(3000, 0);
        assertThat(result.pending()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.oldest()).isNotNull();
        var slow = QueueMetricQuery.source(source, "SELECT SUM(X) pending, 0 failed, CURRENT_TIMESTAMP oldest FROM SYSTEM_RANGE(1,1000000000)");
        assertTimeoutPreemptively(Duration.ofSeconds(6), () -> assertThatThrownBy(slow::get).isInstanceOf(org.springframework.dao.DataAccessException.class));
        assertThat(source.active.get()).isZero();
        assertThat(source.timeouts).endsWith(3000, 0);
        assertThat(query.get()).isEqualTo(result);
    }

    private static final class TrackingSource extends DriverManagerDataSource {
        final java.util.concurrent.atomic.AtomicInteger active = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.List<Integer> timeouts = new java.util.concurrent.CopyOnWriteArrayList<>();
        TrackingSource() { super("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""); }
        @Override public java.sql.Connection getConnection() throws java.sql.SQLException {
            var connection = super.getConnection();
            var closed = new java.util.concurrent.atomic.AtomicBoolean();
            active.incrementAndGet();
            return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{java.sql.Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("setNetworkTimeout")) timeouts.add((Integer) args[1]);
                        try { return method.invoke(connection, args); }
                        catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                        finally { if (method.getName().equals("close") && closed.compareAndSet(false, true)) active.decrementAndGet(); }
                    });
        }
    }
}
