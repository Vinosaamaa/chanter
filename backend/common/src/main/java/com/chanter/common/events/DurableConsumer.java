package com.chanter.common.events;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** One database lock serializes the small launch-scale projection transactions across processes. */
public final class DurableConsumer {
    public static final String SCHEMA = """
        CREATE TABLE durable_consumer_lock (id INT PRIMARY KEY);
        INSERT INTO durable_consumer_lock VALUES (1);
        CREATE TABLE durable_event_cursor (
            producer VARCHAR(32) NOT NULL, aggregate_key VARCHAR(300) NOT NULL,
            revision BIGINT NOT NULL, event_id UUID NOT NULL, deleted BOOLEAN NOT NULL,
            processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (producer, aggregate_key)
        )
        """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public DurableConsumer(JdbcTemplate jdbc, TransactionTemplate tx) { this.jdbc = jdbc; this.tx = tx; }

    public boolean apply(DurableEvent event, boolean deleted, Runnable projection) {
        event.validate();
        return Boolean.TRUE.equals(tx.execute(status -> {
            jdbc.queryForObject("SELECT id FROM durable_consumer_lock WHERE id=1 FOR UPDATE", Integer.class);
            List<Cursor> cursors = jdbc.query("""
                SELECT revision, deleted FROM durable_event_cursor WHERE producer=? AND aggregate_key=?
                """, (rs, row) -> new Cursor(rs.getLong(1), rs.getBoolean(2)), event.producer(), event.aggregateKey());
            if (!cursors.isEmpty() && (cursors.getFirst().deleted() || cursors.getFirst().revision() >= event.revision())) return false;
            projection.run();
            if (cursors.isEmpty()) {
                jdbc.update("""
                    INSERT INTO durable_event_cursor (producer, aggregate_key, revision, event_id, deleted)
                    VALUES (?, ?, ?, ?, ?)
                    """, event.producer(), event.aggregateKey(), event.revision(), event.id(), deleted);
            } else {
                jdbc.update("""
                    UPDATE durable_event_cursor SET revision=?, event_id=?, deleted=?, processed_at=CURRENT_TIMESTAMP
                    WHERE producer=? AND aggregate_key=?
                    """, event.revision(), event.id(), deleted, event.producer(), event.aggregateKey());
            }
            return true;
        }));
    }
    private record Cursor(long revision, boolean deleted) { }
}
