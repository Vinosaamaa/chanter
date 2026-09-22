package com.chanter.community.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.lifecycle.TerminalJournal;
import com.chanter.common.lifecycle.TerminalReapplyStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;

class DeletedStudyServerScopeTest {
    private static final String TOKEN="fixture-community-internal-token-at-least-32";
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    DeletedStudyServerScope scopes;
    TerminalReapplyStore terminal;
    com.chanter.common.lifecycle.AccountDeletionParticipant participant;
    com.chanter.common.lifecycle.AccountDeletionProtocol protocol;
    com.chanter.community.infra.JdbcStudyServerRepository servers;
    com.fasterxml.jackson.databind.ObjectMapper mapper;
    MockMvc http;
    @BeforeEach void setup() {
        var data=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(data).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(data); var transactions=new DataSourceTransactionManager(data);
        tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        scopes=new DeletedStudyServerScope(jdbc,tx,false,() -> terminal);
        mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var snapshots=new com.chanter.common.lifecycle.ExportSnapshotStore(jdbc,tx,mapper,java.time.Clock.systemUTC(),"community");
        var ownership=new CommunityOwnershipFence(jdbc);
        var config=new CommunityTerminalConfiguration();
        terminal=config.communityTerminalStore(jdbc,transactions,snapshots,ownership,scopes);
        protocol=new com.chanter.common.lifecycle.AccountDeletionProtocol(mapper);
        participant=config.communityDeletionParticipant(jdbc,transactions,
                new com.chanter.common.events.DurableOutbox(jdbc,tx,"community",java.time.Clock.systemUTC()),protocol,terminal,ownership);
        servers=new com.chanter.community.infra.JdbcStudyServerRepository(org.springframework.jdbc.core.simple.JdbcClient.create(jdbc),data,ownership,terminal);
        http=MockMvcBuilders.standaloneSetup(new DeletedStudyServerScopeController(scopes,mapper,TOKEN),
                new com.chanter.common.lifecycle.SourceTerminalRecoveryController(terminal,mapper,TOKEN)).build();
    }

    @Test void scopeAndTerminalAuthorityRollBackWithTheActualGraphAndReplayRetainsTheOriginalIds() throws Exception {
        UUID server=UUID.randomUUID(),course=UUID.randomUUID(),channel=UUID.randomUUID(),courseChannel=UUID.randomUUID();
        seed(server,course,channel,courseChannel);
        var page=page(server); var entry=page.entries().getFirst();
        tx.executeWithoutResult(status -> {
            terminal.reapply(page);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_servers WHERE id=?",Integer.class,server)).isZero();
            assertThat(read(entry,"COURSE",new UUID(0,0),256).ids()).containsExactly(course);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM courses WHERE id=?",Integer.class,course)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_deleted_server_scopes",Integer.class)).isZero();
        http.perform(post("/api/v1/internal/lifecycle/journal/reapply").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(page)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pendingTargets").value(1));
        terminal.reapply(page);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> servers.save(server(server,UUID.randomUUID()))))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(read(entry,"COURSE",new UUID(0,0),256).ids()).containsExactly(course);
        var first=read(entry,"CHANNEL",new UUID(0,0),1);
        assertThat(first.ids()).hasSize(1); assertThat(first.nextAfter()).isEqualTo(first.ids().getFirst());
        var second=read(entry,"CHANNEL",first.nextAfter(),1);
        assertThat(second.nextAfter()).isNull();
        assertThat(java.util.stream.Stream.concat(first.ids().stream(),second.ids().stream()).toList())
                .containsExactlyInAnyOrder(channel,courseChannel);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_channels WHERE id=?",Integer.class,courseChannel)).isZero();
        http.perform(request(entry,"CHANNEL")).andExpect(status().isUnauthorized());
        http.perform(request(entry,"CHANNEL").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.terminalDigest").value(entry.digest())).andExpect(jsonPath("$.ids.length()").value(2));
        http.perform(request(entry,"CHANNEL").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN).param("limit","257"))
                .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> scopes.page(server,entry.revision()+1,entry.eventId(),entry.digest(),"COURSE",new UUID(0,0),1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scopes.page(server,entry.revision(),UUID.randomUUID(),entry.digest(),"COURSE",new UUID(0,0),1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scopes.page(server,entry.revision(),entry.eventId(),"f".repeat(64),"COURSE",new UUID(0,0),1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void actualPreparationReleaseAndTerminalDeliveryShareOneDurableReceiptTransaction() {
        UUID account=UUID.randomUUID(),job=UUID.randomUUID();
        var preparation=new com.chanter.common.lifecycle.AccountDeletionProtocol.Preparation(job,account);
        String key=com.chanter.common.lifecycle.AccountDeletionProtocol.key("ACCOUNT",account);
        var event=new com.chanter.common.events.DurableEvent(UUID.randomUUID(),1,"auth",1,
                com.chanter.common.lifecycle.AccountDeletionProtocol.PREPARE,key,protocol.encode(preparation));
        participant.accept(event); participant.accept(event);
        assertThat(jdbc.queryForObject("SELECT preparation_job FROM lifecycle_account_ownership WHERE account_id=?",UUID.class,account)).isEqualTo(job);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE aggregate_key=?",Integer.class,key)).isEqualTo(1);
        UUID server=UUID.randomUUID();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> servers.save(server(server,account))))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var release=new com.chanter.common.events.DurableEvent(UUID.randomUUID(),1,"auth",2,
                com.chanter.common.lifecycle.AccountDeletionProtocol.RELEASE,key,protocol.encode(preparation));
        participant.accept(release);
        tx.executeWithoutResult(status -> servers.save(server(server,account)));
        var before=terminal.receipt().authority(); UUID terminalId=UUID.randomUUID();
        var now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        String digest=TerminalJournal.digest(1,terminalId,"ACCOUNT",account,now,before.digest());
        var entry=new TerminalJournal.Entry(1,terminalId,"ACCOUNT",account,"DELETE",now,TerminalJournal.RETENTION_POLICY,before.digest(),digest);
        var command=new com.chanter.common.lifecycle.AccountDeletionProtocol.Terminal(job,entry);
        var deletion=new com.chanter.common.events.DurableEvent(UUID.randomUUID(),1,"auth",3,
                com.chanter.common.lifecycle.AccountDeletionProtocol.TERMINAL,key,protocol.encode(command));
        participant.accept(deletion);
        var staleRelease=new com.chanter.common.events.DurableEvent(UUID.randomUUID(),1,"auth",4,release.kind(),key,release.payload());
        participant.accept(staleRelease);
        assertThat(jdbc.queryForObject("SELECT terminal FROM lifecycle_account_ownership WHERE account_id=?",Boolean.class,account)).isTrue();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> servers.save(server(UUID.randomUUID(),account))))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_account_tombstones WHERE account_id=?",Integer.class,account)).isEqualTo(1);
        // Restored ownership is retained pending explicit resolution, never silently transferred or erased.
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM study_servers WHERE id=?",UUID.class,server)).isEqualTo(account);
        assertThat(jdbc.queryForObject("SELECT cleanup_state FROM lifecycle_terminal_targets WHERE target_id=?",String.class,account)).isEqualTo("PENDING");
    }

    @Test void archivedCurrentScopeCanBeStagedBeforeReplayWhenTheRestoredDatabasePredatesTheServer() throws Exception {
        UUID server=UUID.randomUUID(),course=UUID.randomUUID();
        seed(server,course,UUID.randomUUID(),UUID.randomUUID());
        var journal=page(server); var entry=journal.entries().getFirst(); terminal.reapply(journal);
        var coursePage=read(entry,"COURSE",new UUID(0,0),256);
        var channelPage=read(entry,"CHANNEL",new UUID(0,0),256);
        setup(); useRecoveryMode(); // A physical recovery point before this server existed.
        var imported=new com.chanter.common.lifecycle.DeletedScope.Import(entry,coursePage);
        String body=mapper.writeValueAsString(imported);
        http.perform(post("/api/v1/internal/lifecycle/deleted-study-servers/{id}/scope/import",server)
                .contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/internal/lifecycle/deleted-study-servers/{id}/scope/import",UUID.randomUUID())
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        for(String malformed:List.of(body+" {}",body.substring(0,body.length()-1)+",\"unexpected\":true}",
                body.substring(0,body.length()-1)+",\"entry\":null}"," ".repeat(32769))) {
            http.perform(post("/api/v1/internal/lifecycle/deleted-study-servers/{id}/scope/import",server)
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN).contentType("application/json").content(malformed)).andExpect(status().isBadRequest());
        }
        http.perform(post("/api/v1/internal/lifecycle/deleted-study-servers/{id}/scope/import",server)
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(true));
        scopes.importPage(new com.chanter.common.lifecycle.DeletedScope.Import(entry,channelPage));
        scopes.derive(new com.chanter.common.lifecycle.RecoveryScope.Derive(UUID.randomUUID(),entry));
        http.perform(request(entry,"COURSE").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)).andExpect(status().isConflict());
        terminal.reapply(journal);
        assertThat(read(entry,"COURSE",new UUID(0,0),256)).isEqualTo(coursePage);
        assertThat(read(entry,"CHANNEL",new UUID(0,0),256)).isEqualTo(channelPage);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_deleted_server_scopes",Integer.class)).isZero();
        assertThat(terminal.receipt().pendingTargets()).isEqualTo(1); // Graph scope readiness is not all downstream cleanup.
    }

    @Test void restoredOlderGraphCannotBecomeCurrentScopeAndFinalImportReconcilesAnAlreadyAppliedPrefix() throws Exception {
        UUID server=UUID.randomUUID(); seed(server,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var journal=page(server); var entry=journal.entries().getFirst(); terminal.reapply(journal);
        var courses=read(entry,"COURSE",new UUID(0,0),256); var channels=read(entry,"CHANNEL",new UUID(0,0),256);
        setup(); seed(server,courses.ids().getFirst(),channels.ids().get(0),channels.ids().get(1)); useRecoveryMode();
        terminal.reapply(journal);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_servers WHERE id=?",Integer.class,server)).isEqualTo(1);
        http.perform(request(entry,"COURSE").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)).andExpect(status().isConflict());
        scopes.importPage(new com.chanter.common.lifecycle.DeletedScope.Import(entry,courses));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_servers WHERE id=?",Integer.class,server)).isEqualTo(1);
        tx.executeWithoutResult(status -> {
            scopes.importPage(new com.chanter.common.lifecycle.DeletedScope.Import(entry,channels));
            scopes.derive(new com.chanter.common.lifecycle.RecoveryScope.Derive(UUID.randomUUID(),entry));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_servers WHERE id=?",Integer.class,server)).isZero();
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_servers WHERE id=?",Integer.class,server)).isEqualTo(1);
        scopes.importPage(new com.chanter.common.lifecycle.DeletedScope.Import(entry,channels));
        scopes.derive(new com.chanter.common.lifecycle.RecoveryScope.Derive(UUID.randomUUID(),entry));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_servers WHERE id=?",Integer.class,server)).isZero();
        assertThat(read(entry,"COURSE",new UUID(0,0),256)).isEqualTo(courses);
        assertThat(terminal.receipt().authority()).isEqualTo(journal.through());
    }

    @Test void olderHistoricalChildrenReconcileThroughASeparateBoundUnionWithoutChangingCurrentArchive() throws Exception {
        UUID server=UUID.randomUUID(); seed(server,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var journal=page(server); var entry=journal.entries().getFirst(); terminal.reapply(journal);
        var courses=read(entry,"COURSE",new UUID(0,0),256); var channels=read(entry,"CHANNEL",new UUID(0,0),256);
        setup(); UUID historicalCourse=UUID.randomUUID(); seed(server,historicalCourse,UUID.randomUUID(),UUID.randomUUID()); useRecoveryMode();
        scopes.importPage(new com.chanter.common.lifecycle.DeletedScope.Import(entry,courses));
        scopes.importPage(new com.chanter.common.lifecycle.DeletedScope.Import(entry,channels));
        terminal.reapply(journal);
        assertThat(terminal.receipt().pendingTargets()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT study_server_id FROM courses WHERE id=?",UUID.class,historicalCourse)).isEqualTo(server);
        assertThat(read(entry,"COURSE",new UUID(0,0),256)).isEqualTo(courses);
        UUID operation=UUID.randomUUID();
        var derive=new com.chanter.common.lifecycle.RecoveryScope.Derive(operation,entry);
        byte[] body=mapper.writeValueAsBytes(derive);
        http.perform(post("/api/v1/internal/lifecycle/deleted-study-servers/{id}/scope/recovery/derive",server)
                .contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/internal/lifecycle/deleted-study-servers/{id}/scope/recovery/derive",server)
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].scope.ready").value(true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_servers WHERE id=?",Integer.class,server)).isZero();
        var historical=scopes.recoveryPage(new com.chanter.common.lifecycle.RecoveryScope.Read(operation,entry,"COURSE",new UUID(0,0),256));
        assertThat(historical.originalScopeDigest()).isEqualTo(courses.scopeDigest());
        assertThat(historical.page().ids()).containsExactlyInAnyOrder(courses.ids().getFirst(),historicalCourse);
        assertThat(read(entry,"COURSE",new UUID(0,0),256)).isEqualTo(courses);
        var later=scopes.derive(new com.chanter.common.lifecycle.RecoveryScope.Derive(UUID.randomUUID(),entry));
        assertThat(later.getFirst().scope().scopeDigest()).isEqualTo(historical.page().scopeDigest());
    }

    @Test void contradictoryRestoredParentOrOrdinaryModeCannotProduceRecoveryScope() {
        UUID server=UUID.randomUUID(); seed(server,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var journal=page(server); var entry=journal.entries().getFirst(); terminal.reapply(journal);
        var courses=read(entry,"COURSE",new UUID(0,0),256); var channels=read(entry,"CHANNEL",new UUID(0,0),256);
        assertThatThrownBy(() -> scopes.derive(new com.chanter.common.lifecycle.RecoveryScope.Derive(UUID.randomUUID(),entry)))
                .isInstanceOf(IllegalArgumentException.class);
        setup(); seed(UUID.randomUUID(),courses.ids().getFirst(),UUID.randomUUID(),UUID.randomUUID()); useRecoveryMode();
        scopes.importPage(new com.chanter.common.lifecycle.DeletedScope.Import(entry,courses));
        scopes.importPage(new com.chanter.common.lifecycle.DeletedScope.Import(entry,channels));
        assertThatThrownBy(() -> scopes.derive(new com.chanter.common.lifecycle.RecoveryScope.Derive(UUID.randomUUID(),entry)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("contradicts current authority");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_recovery_scopes",Integer.class)).isZero();
    }

    private void useRecoveryMode() {
        scopes=new DeletedStudyServerScope(jdbc,tx,true,UUID.fromString("33333333-3333-4333-8333-333333333333"),() -> terminal);
        var snapshots=new com.chanter.common.lifecycle.ExportSnapshotStore(jdbc,tx,mapper,java.time.Clock.systemUTC(),"community");
        terminal=new CommunityTerminalConfiguration().communityTerminalStore(jdbc,tx.getTransactionManager(),snapshots,new CommunityOwnershipFence(jdbc),scopes);
        http=MockMvcBuilders.standaloneSetup(new DeletedStudyServerScopeController(scopes,mapper,TOKEN),
                new com.chanter.common.lifecycle.SourceTerminalRecoveryController(terminal,mapper,TOKEN)).build();
    }

    private static com.chanter.community.domain.StudyServer server(UUID id,UUID owner) {
        return new com.chanter.community.domain.StudyServer(id,"fixture",null,com.chanter.community.domain.StudyServerType.PERSONAL,
                new com.chanter.community.domain.OwnerRole(owner,com.chanter.community.domain.StudyServerRole.STUDY_SERVER_OWNER),
                com.chanter.community.domain.SaasPlanTier.STARTER,List.of(),Instant.now());
    }

    @Test void missingGraphOrUncommittedTerminalAuthorityCannotBecomeAnEmptySuccessfulScope() throws Exception {
        var missing=page(UUID.randomUUID()); terminal.reapply(missing);
        assertThat(terminal.receipt().pendingTargets()).isEqualTo(1);
        http.perform(request(missing.entries().getFirst(),"COURSE").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN))
                .andExpect(status().isConflict());
        UUID server=UUID.randomUUID(); seed(server,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var staged=page(server).entries().getFirst();
        tx.executeWithoutResult(status -> scopes.capture(staged));
        http.perform(request(staged,"COURSE").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)).andExpect(status().isConflict());
        assertThatThrownBy(() -> scopes.capture(staged)).isInstanceOf(IllegalStateException.class);
    }

    private void seed(UUID server,UUID course,UUID channel,UUID courseChannel) {
        UUID owner=UUID.randomUUID();
        jdbc.update("INSERT INTO study_servers(id,name,owner_user_id,created_at) VALUES (?,'fixture',?,CURRENT_TIMESTAMP)",server,owner);
        jdbc.update("INSERT INTO courses(id,study_server_id,title,instructor_user_id,created_at) VALUES (?,?,'fixture',?,CURRENT_TIMESTAMP)",course,server,owner);
        jdbc.update("INSERT INTO study_server_channels(id,study_server_id,name,kind,position) VALUES (?,?,'server channel','TEXT',0)",channel,server);
        UUID cohort=UUID.randomUUID();
        jdbc.update("INSERT INTO cohorts(id,course_id,name,invite_code) VALUES (?,?,'fixture cohort',?)",cohort,course,UUID.randomUUID().toString());
        jdbc.update("INSERT INTO course_channels(id,course_id,cohort_id,name,kind,position) VALUES (?,?,?,'course channel','TEXT',0)",courseChannel,course,cohort);
    }
    private TerminalJournal.Page page(UUID server) {
        var before=terminal.receipt().authority(); long revision=before.revision()+1; UUID event=UUID.randomUUID();
        var now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        String digest=TerminalJournal.digest(revision,event,"STUDY_SERVER",server,now,before.digest());
        var entry=new TerminalJournal.Entry(revision,event,"STUDY_SERVER",server,"DELETE",now,TerminalJournal.RETENTION_POLICY,before.digest(),digest);
        var head=new TerminalJournal.Watermark(revision,digest);
        return new TerminalJournal.Page(2,before,head,List.of(entry),head);
    }
    private com.chanter.common.lifecycle.DeletedScope.Page read(TerminalJournal.Entry entry,String kind,UUID after,int limit) {
        return scopes.page(entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),kind,after,limit);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(TerminalJournal.Entry entry,String kind) {
        return get("/api/v1/internal/lifecycle/deleted-study-servers/{id}/scope",entry.targetId())
                .param("revision",Long.toString(entry.revision())).param("eventId",entry.eventId().toString()).param("digest",entry.digest()).param("kind",kind);
    }
}
