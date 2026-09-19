package com.chanter.common.events;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DurableConsumerTest {
    @Test void receiptAndEffectAreAtomicAndDeletedSourcesCannotReturn() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute(DurableConsumer.SCHEMA);
        jdbc.execute("CREATE TABLE projection (id INT PRIMARY KEY, title VARCHAR(40))");
        var consumer = new DurableConsumer(jdbc, tx);
        var update = event(2);
        assertThatThrownBy(() -> consumer.apply(update, false, () -> {
            jdbc.update("INSERT INTO projection VALUES (1, 'rollback')");
            throw new IllegalStateException("injected failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM projection", Integer.class)).isZero();
        assertThat(consumer.apply(update, false, () -> jdbc.update("INSERT INTO projection VALUES (1, 'new')"))).isTrue();
        assertThat(consumer.apply(update, false, () -> { throw new AssertionError("duplicate"); })).isFalse();
        assertThat(consumer.apply(event(1), false, () -> { throw new AssertionError("old"); })).isFalse();
        assertThat(consumer.apply(event(3), true, () -> jdbc.update("DELETE FROM projection"))).isTrue();
        var restarted = new DurableConsumer(jdbc, tx);
        assertThat(restarted.apply(event(4), false, () -> { throw new AssertionError("revived"); })).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM projection", Integer.class)).isZero();
    }
    private DurableEvent event(long revision) {
        return new DurableEvent(UUID.randomUUID(), 1, "community", revision, "EVENT", "EVENT:1", "{}");
    }
}
