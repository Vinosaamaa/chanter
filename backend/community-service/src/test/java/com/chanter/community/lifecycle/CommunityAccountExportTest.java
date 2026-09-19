package com.chanter.community.lifecycle;

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

class CommunityAccountExportTest {
    @Test void actualCommunitySchemaExportsOwnMembershipAndContentWithoutPeerDataOrInviteCodes() throws Exception {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(data).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(data);
        Instant now = Instant.parse("2026-09-19T00:00:00Z");
        UUID owner = UUID.randomUUID(); UUID peer = UUID.randomUUID(); UUID server = UUID.randomUUID(); UUID other = UUID.randomUUID();
        jdbc.update("INSERT INTO study_servers(id,name,owner_user_id,created_at) VALUES (?,?,?,?)", server, "Owned Study Server", owner, Timestamp.from(now));
        jdbc.update("INSERT INTO study_servers(id,name,owner_user_id,created_at) VALUES (?,?,?,?)", other, "OTHER_SERVER_CANARY", peer, Timestamp.from(now));
        jdbc.update("INSERT INTO study_server_roles VALUES (?,?,?)", server, owner, "STUDY_SERVER_OWNER");
        jdbc.update("INSERT INTO study_server_roles VALUES (?,?,?)", other, peer, "STUDY_SERVER_OWNER");
        UUID course = UUID.randomUUID(); UUID cohort = UUID.randomUUID(); UUID inviteCode = UUID.randomUUID();
        jdbc.update("INSERT INTO courses(id,study_server_id,title,instructor_user_id,created_at) VALUES (?,?,?,?,?)", course, server, "Owned course", owner, Timestamp.from(now));
        jdbc.update("INSERT INTO cohorts(id,course_id,name,invite_code) VALUES (?,?,?,?)", cohort, course, "Class", inviteCode);
        jdbc.update("INSERT INTO cohort_enrollments(cohort_id,learner_user_id,enrolled_by_user_id,enrolled_at) VALUES (?,?,?,?)", cohort, owner, owner, Timestamp.from(now));
        jdbc.update("INSERT INTO study_server_invitations(id,study_server_id,invited_user_id,email,invited_by_user_id,status,created_at) VALUES (?,?,?,?,?,'PENDING',?)",
                UUID.randomUUID(), server, peer, "PEER_EMAIL_CANARY@example.invalid", owner, Timestamp.from(now));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(data));
        var store = new ExportSnapshotStore(jdbc, tx, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC), "community");
        var request = new ExportSnapshotStore.Request(UUID.randomUUID(), owner, now, now.plusSeconds(86_400));
        var exporter = new CommunityAccountExport(jdbc);
        var manifest = store.capture(request, output -> exporter.capture(owner, output));
        var all = new StringBuilder();
        for (var entry : manifest.entries()) for (int page = 0; page < entry.pageCount(); page++)
            all.append(new String(store.page(request.jobId(), owner, entry.ordinal(), page), StandardCharsets.UTF_8));
        assertThat(all.toString()).contains("Owned Study Server", "Owned course", "STUDY_SERVER_OWNER", cohort.toString())
                .doesNotContain("OTHER_SERVER_CANARY", "PEER_EMAIL_CANARY", inviteCode.toString());
        assertThat(manifest.entries()).hasSize(16);
    }
}
