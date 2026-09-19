package com.chanter.common.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.chanter.common.events.DurableConsumer;
import com.chanter.common.events.DurableEvent;
import com.chanter.common.events.DurableOutbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class ExportParticipantTest {
    final Instant now = Instant.parse("2026-09-19T00:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    DurableOutbox outbox;
    ExportSnapshotStore snapshots;
    AccountExportProtocol protocol;

    @BeforeEach void setup() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(data); tx = new TransactionTemplate(new DataSourceTransactionManager(data));
        jdbc.execute(ExportSnapshotStore.SCHEMA); jdbc.execute(DurableOutbox.SCHEMA); jdbc.execute(DurableConsumer.SCHEMA);
        var mapper = new ObjectMapper().findAndRegisterModules();
        protocol = new AccountExportProtocol(mapper);
        outbox = new DurableOutbox(jdbc, tx, "community", clock);
        snapshots = new ExportSnapshotStore(jdbc, tx, mapper, clock, "community");
    }

    @Test void duplicateRequestPublishesOneIdsOnlyReceiptAndCancellationFencesStaleDelivery() {
        var request = request();
        var participant = participant((user, writer) -> writer.jsonLines("memberships", rows -> rows.add(Map.of("private", "SOURCE_CONTENT_CANARY"))));
        var event = event(request, AccountExportProtocol.REQUESTED, 1);
        participant.accept(event); participant.accept(event);
        var delivery = outbox.claim().orElseThrow();
        assertThat(delivery.destination()).isEqualTo("lifecycle-auth");
        var receipt = protocol.receipt(delivery.event());
        assertThat(receipt.fingerprint()).isEqualTo(snapshots.manifest(request.jobId(), request.accountId()).fingerprint());
        assertThat(delivery.event().payload()).doesNotContain("SOURCE_CONTENT_CANARY");
        outbox.delivered(delivery);
        assertThat(outbox.claim()).isEmpty();
        participant.accept(event(request, AccountExportProtocol.CANCELLED, 3));
        participant.accept(event(request, AccountExportProtocol.REQUESTED, 2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_pages", Integer.class)).isZero();
        var cancelled = protocol.receipt(outbox.claim().orElseThrow().event());
        assertThat(cancelled.state()).isEqualTo("CANCELLED");
    }

    @Test void sourceFailureRollsBackSnapshotCursorAndReceiptSoTheExistingOutboxCanRetry() {
        var request = request();
        var broken = participant((user, writer) -> {
            writer.jsonLines("memberships", rows -> rows.add(Map.of("role", "member")));
            throw new IOException("source unavailable");
        });
        var event = event(request, AccountExportProtocol.REQUESTED, 1);
        assertThatThrownBy(() -> broken.accept(event)).isInstanceOf(ExportSnapshotStore.ExportFailure.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_snapshots", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_event_cursor", Integer.class)).isZero();
        assertThat(outbox.claim()).isEmpty();
        participant((user, writer) -> writer.jsonLines("memberships", rows -> rows.add(Map.of("role", "member")))).accept(event);
        assertThat(outbox.claim()).isPresent();
    }

    @Test void foreignProducerUnknownFieldsDuplicateFieldsAndMismatchedScopeAreRejectedBeforeCapture() {
        var request = request();
        var participant = participant((user, writer) -> { throw new AssertionError("Invalid message reached projection"); });
        String json = protocol.encode(request);
        assertThatThrownBy(() -> participant.accept(new DurableEvent(UUID.randomUUID(), 1, "message", 1,
                AccountExportProtocol.REQUESTED, AccountExportProtocol.key(request.jobId()), json))).isInstanceOf(IllegalArgumentException.class);
        for (String payload : java.util.List.of(json.substring(0, json.length() - 1) + ",\"query\":\"secret\"}",
                json.substring(0, json.length() - 1) + ",\"jobId\":\"" + UUID.randomUUID() + "\"}", json + " {}")) {
            assertThatThrownBy(() -> participant.accept(new DurableEvent(UUID.randomUUID(), 1, "auth", 1,
                    AccountExportProtocol.REQUESTED, AccountExportProtocol.key(request.jobId()), payload))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> participant.accept(new DurableEvent(UUID.randomUUID(), 1, "auth", 1,
                AccountExportProtocol.REQUESTED, AccountExportProtocol.key(UUID.randomUUID()), json))).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_snapshots", Integer.class)).isZero();
    }

    private ExportParticipant participant(AccountExportProjection projection) {
        return new ExportParticipant("community", snapshots, projection, new DurableConsumer(jdbc, tx), outbox, protocol, clock);
    }
    private ExportSnapshotStore.Request request() { return new ExportSnapshotStore.Request(UUID.randomUUID(), UUID.randomUUID(), now, now.plusSeconds(86_400)); }
    private DurableEvent event(ExportSnapshotStore.Request request, String kind, long revision) {
        return new DurableEvent(UUID.randomUUID(), 1, "auth", revision, kind, AccountExportProtocol.key(request.jobId()), protocol.encode(request));
    }
}
