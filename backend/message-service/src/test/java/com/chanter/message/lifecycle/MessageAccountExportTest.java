package com.chanter.message.lifecycle;

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

class MessageAccountExportTest {
    @Test void actualMessageSchemaSeparatesAuthoredTextFromReceivedAndAssignedContent() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(data).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(data); var tx = new TransactionTemplate(new DataSourceTransactionManager(data));
        Instant now = Instant.parse("2026-09-19T00:00:00Z"); var time = Timestamp.from(now);
        UUID owner = UUID.randomUUID(); UUID peer = UUID.randomUUID(); UUID outsider = UUID.randomUUID(); UUID channel = UUID.randomUUID();
        UUID ownedMessage = UUID.randomUUID(); UUID peerMessage = UUID.randomUUID(); UUID question = UUID.randomUUID();
        jdbc.update("INSERT INTO direct_messages VALUES (?,?,?,?,?)", UUID.randomUUID(), owner, peer, "MY_DIRECT_TEXT", time);
        jdbc.update("INSERT INTO direct_messages VALUES (?,?,?,?,?)", UUID.randomUUID(), peer, owner, "RECEIVED_BODY_CANARY", time);
        jdbc.update("INSERT INTO direct_messages VALUES (?,?,?,?,?)", UUID.randomUUID(), peer, outsider, "OUTSIDER_DM_CANARY", time);
        jdbc.update("INSERT INTO channel_messages VALUES (?,?,?,?,?)", ownedMessage, channel, owner, "MY_CHANNEL_TEXT", time);
        jdbc.update("INSERT INTO channel_messages VALUES (?,?,?,?,?)", peerMessage, channel, peer, "PEER_CHANNEL_CANARY", time);
        jdbc.update("INSERT INTO support_questions VALUES (?,?,?,?,?,?,?,?)", question, ownedMessage, channel, owner, "MY_SUPPORT_TEXT", "OPEN", "IDEMPOTENCY_CANARY", time);
        jdbc.update("INSERT INTO support_question_replies VALUES (?,?,?,?,?)", UUID.randomUUID(), question, owner, "MY_REPLY_TEXT", time);
        jdbc.update("INSERT INTO support_question_replies VALUES (?,?,?,?,?)", UUID.randomUUID(), question, peer, "PEER_REPLY_CANARY", time);
        jdbc.update("INSERT INTO approved_faqs VALUES (?,?,?,?,?,?,?)", UUID.randomUUID(), UUID.randomUUID(), "APPROVED_PEER_QUESTION_CANARY", "APPROVED_PEER_ANSWER_CANARY", owner, time, time);
        jdbc.update("INSERT INTO ta_queue_items VALUES (?,?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), UUID.randomUUID(), question, channel, peer, "ASSIGNED_PEER_QUESTION_CANARY", "PICKED_UP", owner, time, time);
        jdbc.update("INSERT INTO user_blocks VALUES (?,?,?)", owner, peer, time);
        jdbc.update("INSERT INTO user_blocks VALUES (?,?,?)", outsider, owner, time);
        var snapshots = new ExportSnapshotStore(jdbc, tx, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC), "message");
        var projection = new MessageAccountExport(jdbc);
        var request = new ExportSnapshotStore.Request(UUID.randomUUID(), owner, now, now.plusSeconds(86400));
        var manifest = snapshots.capture(request, output -> projection.capture(owner, output));
        var text = new StringBuilder();
        for (var entry : manifest.entries()) for (int page = 0; page < entry.pageCount(); page++)
            text.append(new String(snapshots.page(request.jobId(), owner, entry.ordinal(), page), StandardCharsets.UTF_8));
        assertThat(text.toString()).contains("MY_DIRECT_TEXT", "MY_CHANNEL_TEXT", "MY_SUPPORT_TEXT", "MY_REPLY_TEXT")
                .doesNotContain("RECEIVED_BODY_CANARY", "OUTSIDER_DM_CANARY", "PEER_CHANNEL_CANARY", "PEER_REPLY_CANARY",
                        "APPROVED_PEER_QUESTION_CANARY", "APPROVED_PEER_ANSWER_CANARY", "ASSIGNED_PEER_QUESTION_CANARY", "IDEMPOTENCY_CANARY", outsider.toString());
        assertThat(manifest.entries()).hasSize(11);
    }
}
