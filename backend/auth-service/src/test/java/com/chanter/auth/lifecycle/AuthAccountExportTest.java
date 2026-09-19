package com.chanter.auth.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class AuthAccountExportTest {
    @Test void actualAuthSchemaExportsOnlyTheRequestedPersonsExplicitFieldsAndNoCredentialCanaries() throws Exception {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(data).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(data);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(data));
        Instant now = Instant.parse("2026-09-19T00:00:00Z");
        UUID owner = UUID.randomUUID(); UUID other = UUID.randomUUID(); UUID session = UUID.randomUUID();
        jdbc.update("INSERT INTO auth_users(id,email,password_hash,display_name) VALUES (?,?,?,?)", owner, "owner@example.invalid", "PASSWORD_CANARY", "Owner");
        jdbc.update("INSERT INTO auth_users(id,email,password_hash,display_name) VALUES (?,?,?,?)", other, "OTHER_PERSON_CANARY@example.invalid", "OTHER_PASSWORD", "OTHER_NAME_CANARY");
        jdbc.update("INSERT INTO auth_oauth_accounts(id,user_id,provider,provider_subject) VALUES (?,?,?,?)", UUID.randomUUID(), owner, "google", "owned-provider-subject");
        jdbc.update("INSERT INTO auth_oauth_accounts(id,user_id,provider,provider_subject) VALUES (?,?,?,?)", UUID.randomUUID(), other, "google", "OTHER_PROVIDER_CANARY");
        jdbc.update("INSERT INTO auth_sessions(id,user_id,created_at,last_used_at,expires_at,user_agent) VALUES (?,?,?,?,?,?)",
                session, owner, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.plusSeconds(3600)), "Owned browser");
        jdbc.update("INSERT INTO auth_refresh_tokens(id,user_id,session_id,token_hash,expires_at) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), owner, session, "REFRESH_CANARY", Timestamp.from(now.plusSeconds(3600)));
        jdbc.update("INSERT INTO auth_email_tokens(id,user_id,token_hash,purpose,expires_at) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), owner, "RESET_CANARY", "PASSWORD_RESET", Timestamp.from(now.plusSeconds(3600)));
        var store = new ExportSnapshotStore(jdbc, tx, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC), "auth");
        var request = new ExportSnapshotStore.Request(UUID.randomUUID(), owner, now, now.plusSeconds(86_400));
        var exporter = new AuthAccountExport(jdbc);
        var manifest = store.capture(request, output -> exporter.capture(owner, output));
        var all = new StringBuilder();
        for (var entry : manifest.entries()) for (int page = 0; page < entry.pageCount(); page++)
            all.append(new String(store.page(request.jobId(), owner, entry.ordinal(), page), StandardCharsets.UTF_8));
        assertThat(all.toString()).contains("owner@example.invalid", "owned-provider-subject", "Owned browser")
                .doesNotContain("PASSWORD_CANARY", "REFRESH_CANARY", "RESET_CANARY", "OTHER_PERSON_CANARY", "OTHER_PROVIDER_CANARY", "OTHER_NAME_CANARY");
        assertThat(manifest.entries()).extracting(ExportSnapshotStore.Entry::path)
                .containsExactly("profile.jsonl", "linked_accounts.jsonl", "sessions.jsonl", "coverage.jsonl");
    }
}
