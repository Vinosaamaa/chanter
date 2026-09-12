package com.chanter.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SessionMigrationTest {
    @Test
    void upgradingPopulatedLegacySchemaPreservesHistoryAndRevokesJavascriptCredentials() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("2").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        jdbc.update("INSERT INTO auth_users (id, email, password_hash, display_name) VALUES (?, ?, ?, ?)",
                userId, "migration@study.local", "test-hash", "Migration user");
        jdbc.update("INSERT INTO auth_refresh_tokens (id, user_id, token_hash, expires_at) VALUES (?, ?, ?, ?)",
                tokenId, userId, "legacy-token-hash", Timestamp.from(Instant.now().plusSeconds(3600)));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("3").load().migrate();
        var reopenedRepository = new JdbcRefreshTokenRepository(new JdbcTemplate(dataSource));
        assertThat(reopenedRepository.findActiveSessions(userId, Instant.now())).isEmpty();
        assertThat(reopenedRepository.findSessionIdByTokenHash("legacy-token-hash")).contains(tokenId);
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM auth_sessions WHERE id = ?", Boolean.class, tokenId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM auth_refresh_tokens WHERE id = ?", Boolean.class, tokenId)).isTrue();
    }
}
