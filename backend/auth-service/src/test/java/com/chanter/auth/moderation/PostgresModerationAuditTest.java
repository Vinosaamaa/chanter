package com.chanter.auth.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named="DURABLE_EVENTS_TEST_JDBC_URL",matches=".+")
class PostgresModerationAuditTest {
    @Test void databaseRejectsHistoryUpdatesDeletesAndTruncationAndReconnectKeepsTheAudit() {
        String base = System.getenv("DURABLE_EVENTS_TEST_JDBC_URL");
        String schema = "moderation_"+UUID.randomUUID().toString().replace("-", "");
        var owner = new JdbcTemplate(new DriverManagerDataSource(base,"events_test","events_test"));
        owner.execute("CREATE SCHEMA "+schema);
        String url = base+(base.contains("?") ? "&" : "?")+"currentSchema="+schema;
        try {
            var source = new DriverManagerDataSource(url,"events_test","events_test");
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
            var jdbc = new JdbcTemplate(source);
            new ModerationAudit(jdbc).append(UUID.randomUUID(),"EVIDENCE_READ","report:opaque","Investigate assigned report",
                    UUID.randomUUID(),"","");
            assertThatThrownBy(() -> jdbc.update("UPDATE moderation_audit SET reason='altered'"))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM moderation_audit"))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.execute("TRUNCATE moderation_audit"))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.execute("TRUNCATE moderation_notes"))
                    .isInstanceOf(DataAccessException.class);
            var reconnected = new JdbcTemplate(new DriverManagerDataSource(url,"events_test","events_test"));
            assertThat(reconnected.queryForObject("SELECT reason FROM moderation_audit",String.class))
                    .isEqualTo("Investigate assigned report");
        } finally {
            // Only this test's validated UUID-derived schema; never shared application data.
            owner.execute("DROP SCHEMA "+schema+" CASCADE");
        }
    }
}
