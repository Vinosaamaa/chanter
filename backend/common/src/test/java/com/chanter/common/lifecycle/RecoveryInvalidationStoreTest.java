package com.chanter.common.lifecycle;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class RecoveryInvalidationStoreTest {
    @Test void receiptRollsBackWithEffectsAndRetryBindsTheOriginalIdentityAndAuthority() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(data);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(data));
        jdbc.execute(RecoveryInvalidationStore.SCHEMA);
        jdbc.execute("CREATE TABLE credentials(id INT PRIMARY KEY,active BOOLEAN); INSERT INTO credentials VALUES (1,TRUE)");
        var store = new RecoveryInvalidationStore(jdbc, Clock.systemUTC(), "agent");
        var authority = new TerminalJournal.Watermark(0, TerminalJournal.GENESIS);
        var request = new RecoveryInvalidationStore.Request(UUID.randomUUID(), authority);
        assertThatThrownBy(() -> store.invalidate(request, authority, now -> {})).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tx.execute(status -> store.invalidate(request, authority, now -> {
            jdbc.update("UPDATE credentials SET active=FALSE"); throw new IllegalStateException("failure after effect");
        }))).hasMessageContaining("failure after effect");
        assertThat(jdbc.queryForObject("SELECT active FROM credentials", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_recovery_invalidations", Integer.class)).isZero();
        var receipt = tx.execute(status -> store.invalidate(request, authority, now -> jdbc.update("UPDATE credentials SET active=FALSE")));
        assertThat(receipt.scope()).isEqualTo("ALL_PENDING_NATIVE_REQUESTS");
        assertThat(jdbc.queryForObject("SELECT active FROM credentials", Boolean.class)).isFalse();
        var restarted = new RecoveryInvalidationStore(jdbc, Clock.systemUTC(), "agent");
        var repeated = tx.execute(status -> restarted.invalidate(request, authority, now -> { throw new AssertionError("duplicate effect"); }));
        assertThat(repeated).isEqualTo(receipt);
        var changed = new TerminalJournal.Watermark(1, "a".repeat(64));
        assertThatThrownBy(() -> tx.execute(status -> restarted.invalidate(request, changed, now -> {})))
                .hasMessageContaining("applied prefix");
        assertThatThrownBy(() -> tx.execute(status -> restarted.invalidate(new RecoveryInvalidationStore.Request(request.recoveryId(), changed), changed, now -> {})))
                .hasMessageContaining("identity changed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_recovery_invalidations", Integer.class)).isEqualTo(1);
    }
}
