package com.chanter.common.telemetry;

import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** Executes a source-owned aggregate with fixed statement and socket deadlines. No caller supplies SQL over an API. */
public final class QueueMetricQuery {
    private QueueMetricQuery() { }
    public static Supplier<QueueMetrics.Snapshot> source(DataSource source, String sql) {
        var jdbc = new JdbcTemplate(source);
        return () -> jdbc.execute((ConnectionCallback<QueueMetrics.Snapshot>) connection -> {
            int previousTimeout = connection.getNetworkTimeout();
            connection.setNetworkTimeout(Runnable::run, 3000);
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(2);
                try (var rows = statement.executeQuery(sql)) {
                    if (!rows.next()) throw new java.sql.SQLException("Missing queue measurement");
                    var oldest = rows.getTimestamp("oldest");
                    var snapshot = new QueueMetrics.Snapshot(rows.getLong("pending"), rows.getLong("failed"),
                            oldest == null ? null : oldest.toInstant());
                    if (rows.next()) throw new java.sql.SQLException("Ambiguous queue measurement");
                    return snapshot;
                }
            } finally { if (!connection.isClosed()) connection.setNetworkTimeout(Runnable::run, previousTimeout); }
        });
    }
}
