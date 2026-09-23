package com.chanter.notification.lifecycle;

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

class NotificationAccountExportTest {
    @Test void actualNotificationSchemaExportsOwnDeliveryStateWithoutBypassingSourceContentAuthorization() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(data).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(data); var tx = new TransactionTemplate(new DataSourceTransactionManager(data));
        Instant now = Instant.parse("2026-09-19T00:00:00Z"); UUID owner = UUID.randomUUID(); UUID other = UUID.randomUUID(); UUID owned = UUID.randomUUID();
        for (UUID account : java.util.List.of(owner, other)) jdbc.update("""
            INSERT INTO notifications(id,user_id,kind,filter_bucket,title,body_preview,course_label,href,source_type,source_id,created_at,read_at)
            VALUES (?,?, 'ANSWER_READY','all','SOURCE_TITLE_CANARY','SOURCE_BODY_CANARY','SOURCE_LABEL_CANARY','/private-link-canary','answer',?,?,?)
            """, account.equals(owner) ? owned : UUID.randomUUID(), account, UUID.randomUUID(), Timestamp.from(now), Timestamp.from(now));
        var store = new ExportSnapshotStore(jdbc, tx, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC), "notification");
        var exporter = new NotificationAccountExport(jdbc);
        var request = new ExportSnapshotStore.Request(UUID.randomUUID(), owner, now, now.plusSeconds(86400));
        var manifest = store.capture(request, output -> exporter.capture(owner, output));
        String text = new String(store.page(request.jobId(), owner, 0, 0), StandardCharsets.UTF_8);
        assertThat(text).contains(owned.toString(), "ANSWER_READY", "read_at").doesNotContain(other.toString(), "SOURCE_TITLE_CANARY", "SOURCE_BODY_CANARY", "SOURCE_LABEL_CANARY", "/private-link-canary");
        assertThat(manifest.entries()).hasSize(2);
    }
}
