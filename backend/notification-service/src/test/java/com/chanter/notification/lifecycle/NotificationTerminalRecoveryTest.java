package com.chanter.notification.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.DurableEvent;
import com.chanter.common.events.NotificationEventWriter;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.chanter.common.lifecycle.TerminalJournal;
import com.chanter.common.lifecycle.TerminalReapplyStore;
import com.chanter.notification.application.NotificationRepository;
import com.chanter.notification.application.NotificationService;
import com.chanter.notification.domain.NotificationKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties="chanter.events.dispatch-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationTerminalRecoveryTest {
    private static final String TOKEN="test-internal-service-token-for-notification";
    @Autowired MockMvc http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TerminalReapplyStore terminal;
    @Autowired ExportSnapshotStore snapshots;
    @Autowired NotificationService notifications;
    @Autowired com.chanter.common.lifecycle.AccountDeletionProtocol deletionProtocol;
    @Autowired com.chanter.common.lifecycle.DeletedScopeStore scopes;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.chanter.notification.application.NotificationVisibility visibility;

    @Test void emptyProducerFinalsAndReportedCompletionCommitAtomicallyThroughThePrivateRoute() throws Exception {
        var page=nextPage("ACCOUNT",UUID.randomUUID(),Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        var entry=page.entries().getFirst();UUID job=UUID.randomUUID();
        terminal.reapply(page);
        var command=new DurableEvent(UUID.randomUUID(),1,"auth",entry.revision(),com.chanter.common.lifecycle.AccountDeletionProtocol.TERMINAL,
                com.chanter.common.lifecycle.AccountDeletionProtocol.key("ACCOUNT",entry.targetId()),
                deletionProtocol.encode(new com.chanter.common.lifecycle.AccountDeletionProtocol.Terminal(job,entry)));
        postLifecycle(command).andExpect(status().isNoContent());
        var community=new com.chanter.common.lifecycle.ErasedContent.Completion(entry,"community",0,0);
        var first=new DurableEvent(UUID.randomUUID(),1,"community",100,com.chanter.common.lifecycle.ErasedContent.FINAL,community.key(),mapper.writeValueAsString(community));
        postLifecycle(first).andExpect(status().isNoContent());
        var message=new com.chanter.common.lifecycle.ErasedContent.Completion(entry,"message",0,0);
        var last=new DurableEvent(UUID.randomUUID(),1,"message",100,com.chanter.common.lifecycle.ErasedContent.FINAL,message.key(),mapper.writeValueAsString(message));
        jdbc.execute("ALTER TABLE durable_outbox ADD CONSTRAINT reject_completion CHECK(kind<>'ACCOUNT_DELETE_RECEIPT' OR aggregate_key<>'"+com.chanter.common.lifecycle.AccountDeletionProtocol.key("ACCOUNT",entry.targetId())+"' OR payload NOT LIKE '%COMPLETE%')");
        try { assertThatThrownBy(() -> postLifecycle(last)).hasRootCauseInstanceOf(org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class); }
        finally { jdbc.execute("ALTER TABLE durable_outbox DROP CONSTRAINT reject_completion"); }
        assertThat(jdbc.queryForObject("SELECT cleanup_state FROM lifecycle_terminal_targets WHERE target_id=?",String.class,entry.targetId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_content_received_final WHERE account_id=?",Integer.class,entry.targetId())).isEqualTo(1);
        postLifecycle(last).andExpect(status().isNoContent());postLifecycle(last).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT cleanup_state FROM lifecycle_terminal_targets WHERE target_id=?",String.class,entry.targetId())).isEqualTo("COMPLETE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind='ACCOUNT_DELETE_RECEIPT' AND aggregate_key=? AND payload LIKE '%COMPLETE%'",Integer.class,
                com.chanter.common.lifecycle.AccountDeletionProtocol.key("ACCOUNT",entry.targetId()))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind='ACCOUNT_CONTENT_COMPLETE' AND aggregate_key=?",Integer.class,last.aggregateKey())).isEqualTo(1);
    }
    private org.springframework.test.web.servlet.ResultActions postLifecycle(DurableEvent event) throws Exception {
        return http.perform(post("/api/v1/internal/lifecycle/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event)));
    }

    @Test void actualNotificationsExportsAndPermanentFenceCommitTogetherAndDelayedEventsCannotRestoreThem() throws Exception {
        UUID owner=UUID.randomUUID(), other=UUID.randomUUID(), server=UUID.randomUUID(), resource=UUID.randomUUID();
        var own=create(owner,server,resource);
        var retained=create(other,UUID.randomUUID(),UUID.randomUUID());
        Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var request=new ExportSnapshotStore.Request(UUID.randomUUID(),owner,now,now.plusSeconds(86400));
        snapshots.capture(request,output -> new NotificationAccountExport(jdbc).capture(owner,output));
        var page=nextPage("ACCOUNT",owner,now);
        int alreadyPending=terminal.receipt().pendingTargets();
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            terminal.reapply(page);
            assertThat(count(owner)).isZero();
            status.setRollbackOnly();
        });
        assertThat(count(owner)).isEqualTo(1);
        assertThat(terminal.terminal("ACCOUNT",owner)).isFalse();
        http.perform(post("/api/v1/internal/lifecycle/journal/reapply").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(page)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pendingTargets").value(alreadyPending+1));
        assertThat(count(owner)).isZero();
        assertThat(count(other)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_pages WHERE snapshot_id=?",Integer.class,request.jobId())).isZero();
        assertThatThrownBy(() -> snapshots.capture(request,output -> new NotificationAccountExport(jdbc).capture(owner,output)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(terminal.reapply(page).authority()).isEqualTo(page.next());

        var payload=mapper.createObjectNode().put("userId",owner.toString()).put("kind","SUPPORT_QUESTION_CREATED")
                .put("title","late private payload").put("href","/app/inbox").put("sourceType","RESOURCE")
                .put("sourceId",resource.toString()).put("studyServerId",server.toString());
        var event=new DurableEvent(UUID.randomUUID(),1,"community",1,"NOTIFICATION",
                NotificationEventWriter.aggregateKey(owner,"RESOURCE",resource,"SUPPORT_QUESTION_CREATED"),mapper.writeValueAsString(payload));
        for(int attempt=0;attempt<2;attempt++) http.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event))).andExpect(status().isNoContent());
        assertThat(count(owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE id=?",Integer.class,retained.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE id=?",Integer.class,own.id())).isZero();
    }

    @Test void serverAndResourceAuthorityRemoveOnlyOwnedDerivedRowsAndRejectMissingPrivateCredential() throws Exception {
        UUID owner=UUID.randomUUID(), server=UUID.randomUUID(), otherServer=UUID.randomUUID(), resource=UUID.randomUUID();
        create(owner,server,UUID.randomUUID());
        create(owner,otherServer,resource);
        var retained=create(owner,otherServer,UUID.randomUUID());
        var serverPage=nextPage("STUDY_SERVER",server,Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        http.perform(post("/api/v1/internal/lifecycle/journal/reapply").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(serverPage))).andExpect(status().isUnauthorized());
        assertThat(count(owner)).isEqualTo(3);
        terminal.reapply(serverPage);
        terminal.reapply(nextPage("RESOURCE",resource,Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)));
        create(owner,server,UUID.randomUUID()); create(owner,otherServer,resource);
        assertThat(count(owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id FROM notifications WHERE user_id=?",UUID.class,owner)).isEqualTo(retained.id());
        http.perform(post("/api/v1/internal/lifecycle/journal/reapply").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"schemaVersion\":2,\"schemaVersion\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test void finalScopeImportErasesLegacyCourseChannelPreviewsAndFencesDelayedDeliveryWithoutAServerId() throws Exception {
        UUID owner=UUID.randomUUID(),server=UUID.randomUUID(),course=UUID.randomUUID(),channel=UUID.randomUUID(),source=UUID.randomUUID();
        var command=new NotificationRepository.CreateCommand(owner,NotificationKind.SUPPORT_QUESTION_ANSWERED,null,
                "private title","private answer preview",null,"/app/inbox","SUPPORT_QUESTION",source,null,course,null,channel);
        notifications.create(command);
        var page=nextPage("STUDY_SERVER",server,Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        var entry=page.entries().getFirst();
        var courseScope=scope(entry,"COURSE",course); var channelScope=scope(entry,"CHANNEL",channel);
        assertThatThrownBy(() -> scopes.accept(courseScope)).isInstanceOf(IllegalArgumentException.class);
        terminal.reapply(page);
        assertThat(count(owner)).isEqualTo(1);
        scopes.accept(courseScope);
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> { scopes.accept(channelScope); assertThat(count(owner)).isZero(); status.setRollbackOnly(); });
        assertThat(count(owner)).isEqualTo(1);
        byte[] body=mapper.writeValueAsBytes(channelScope);
        http.perform(post("/api/v1/internal/lifecycle/deleted-study-servers/{id}/scope/import",server)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        for(int retry=0;retry<2;retry++) http.perform(post("/api/v1/internal/lifecycle/deleted-study-servers/{id}/scope/import",server)
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(true));
        assertThat(count(owner)).isZero();
        var cleanup=tx.execute(status -> terminal.cleanup(entry));
        assertThat(cleanup).isEqualTo(TerminalReapplyStore.Cleanup.COMPLETE);
        var payload=mapper.createObjectNode().put("userId",owner.toString()).put("kind","SUPPORT_QUESTION_ANSWERED")
                .put("title","late private title").put("bodyPreview","late private answer preview").put("href","/app/inbox")
                .put("sourceType","SUPPORT_QUESTION").put("sourceId",source.toString()).put("courseId",course.toString()).put("channelId",channel.toString());
        var event=new DurableEvent(UUID.randomUUID(),1,"message",20,"NOTIFICATION",
                NotificationEventWriter.aggregateKey(owner,"SUPPORT_QUESTION",source,"SUPPORT_QUESTION_ANSWERED"),mapper.writeValueAsString(payload));
        http.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event))).andExpect(status().isNoContent());
        notifications.create(command);
        assertThat(count(owner)).isZero();
    }

    private static com.chanter.common.lifecycle.DeletedScope.Import scope(TerminalJournal.Entry entry,String kind,UUID id) {
        String digest=com.chanter.common.lifecycle.DeletedScope.nextDigest(
                com.chanter.common.lifecycle.DeletedScope.startDigest(entry.digest(),kind,1),id);
        return new com.chanter.common.lifecycle.DeletedScope.Import(entry,new com.chanter.common.lifecycle.DeletedScope.Page(1,
                entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),kind,new UUID(0,0),1,digest,List.of(id),null));
    }

    @Test void aWriterOverlappingTerminalCommitCannotRecreateNotificationPayload() throws Exception {
        UUID owner=UUID.randomUUID(), server=UUID.randomUUID(), resource=UUID.randomUUID();
        create(owner,server,resource);
        var page=nextPage("ACCOUNT",owner,Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        var applied=new java.util.concurrent.CountDownLatch(1);
        var writerStarted=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var deletion=workers.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                terminal.reapply(page); applied.countDown();
                try { if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout"); }
                catch(InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
            }));
            assertThat(applied.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var writer=workers.submit(() -> { writerStarted.countDown(); return create(owner,server,resource); });
            assertThat(writerStarted.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            release.countDown();
            deletion.get(5,java.util.concurrent.TimeUnit.SECONDS);
            writer.get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(count(owner)).isZero();
            assertThat(terminal.terminal("ACCOUNT",owner)).isTrue();
        } finally { release.countDown(); }
    }

    @Test void ordinaryTerminalDeliveryCommitsItsReceiptOnceAndReceiptFailureRollsBackErasure() throws Exception {
        UUID owner=UUID.randomUUID(),eventId=UUID.randomUUID(),job=UUID.randomUUID();
        create(owner,UUID.randomUUID(),UUID.randomUUID());
        Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        // Normal delivery may lead the externally replayed prefix. It must not claim missing intervening entries.
        long revision=10000;
        String digest=TerminalJournal.digest(revision,eventId,"ACCOUNT",owner,now,TerminalJournal.GENESIS);
        var entry=new TerminalJournal.Entry(revision,eventId,"ACCOUNT",owner,"DELETE",now,
                TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,digest);
        var command=new com.chanter.common.lifecycle.AccountDeletionProtocol.Terminal(job,entry);
        String key=com.chanter.common.lifecycle.AccountDeletionProtocol.key("ACCOUNT",owner);
        var event=new DurableEvent(UUID.randomUUID(),1,"auth",10,
                com.chanter.common.lifecycle.AccountDeletionProtocol.TERMINAL,key,deletionProtocol.encode(command));
        byte[] body=mapper.writeValueAsBytes(event);
        var before=terminal.receipt().authority();
        jdbc.execute("ALTER TABLE durable_outbox ADD CONSTRAINT test_reject_deletion_receipt CHECK(kind<>'ACCOUNT_DELETE_RECEIPT' OR aggregate_key<>'"+key+"')");
        try {
            assertThatThrownBy(() -> http.perform(post("/api/v1/internal/lifecycle/events")
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN).contentType(MediaType.APPLICATION_JSON).content(body)))
                    .hasRootCauseInstanceOf(org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class);
            assertThat(count(owner)).isEqualTo(1);
            assertThat(terminal.terminal("ACCOUNT",owner)).isFalse();
        } finally { jdbc.execute("ALTER TABLE durable_outbox DROP CONSTRAINT test_reject_deletion_receipt"); }
        for(int attempt=0;attempt<2;attempt++) http.perform(post("/api/v1/internal/lifecycle/events")
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        assertThat(count(owner)).isZero();
        assertThat(terminal.receipt().authority()).isEqualTo(before);
        var receipts=jdbc.query("SELECT payload FROM durable_outbox WHERE aggregate_key=? AND kind='ACCOUNT_DELETE_RECEIPT'",(rs,row)->rs.getString(1),key);
        assertThat(receipts).hasSize(1);
        var receipt=mapper.readValue(receipts.getFirst(),com.chanter.common.lifecycle.AccountDeletionProtocol.Receipt.class);
        receipt.validate();
        assertThat(receipt.jobId()).isEqualTo(job);
        assertThat(receipt.terminalDigest()).isEqualTo(digest);
        assertThat(receipt.state()).isEqualTo("PENDING");
        var forged=new DurableEvent(UUID.randomUUID(),1,"community",11,event.kind(),key,event.payload());
        http.perform(post("/api/v1/internal/lifecycle/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(forged))).andExpect(status().isBadRequest());
    }
    @Test void authoredPreviewErasureFencesClaimedAndDirectWritesWithoutRemovingLaterHumanUpdate() throws Exception {
        UUID recipient=UUID.randomUUID(),server=UUID.randomUUID(),announcement=UUID.randomUUID(),question=UUID.randomUUID();
        var authored=new NotificationRepository.CreateCommand(recipient,NotificationKind.SUPPORT_QUESTION_CREATED,null,
                "author title","author preview",null,"/app/inbox","ANNOUNCEMENT",announcement,server,null,null,null);
        notifications.create(authored);
        var human=new NotificationRepository.CreateCommand(recipient,NotificationKind.SUPPORT_QUESTION_ANSWERED,null,
                "human answer","private human body","private label","/app/inbox","SUPPORT_QUESTION",question,server,null,null,null);
        var humanRow=notifications.create(human);
        jdbc.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP,done_at=CURRENT_TIMESTAMP WHERE id=?",humanRow.id());
        var metadata=jdbc.queryForMap("SELECT id,created_at,read_at,done_at FROM notifications WHERE id=?",humanRow.id());
        var page=nextPage("ACCOUNT",UUID.randomUUID(),Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));terminal.reapply(page);
        for(var ref:List.of(new com.chanter.common.lifecycle.ErasedContent.Ref("ANNOUNCEMENT",announcement),new com.chanter.common.lifecycle.ErasedContent.Ref("QUESTION_PREVIEW",question))) {
            String producer=ref.kind().equals("ANNOUNCEMENT") ? "community" : "message";
            var batch=new com.chanter.common.lifecycle.ErasedContent.Batch(page.entries().getFirst(),List.of(ref));
            var event=new DurableEvent(UUID.randomUUID(),1,producer,30,com.chanter.common.lifecycle.ErasedContent.ERASE,batch.key(UUID.randomUUID()),mapper.writeValueAsString(batch));
            for(int n=0;n<2;n++) http.perform(post("/api/v1/internal/lifecycle/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event))).andExpect(status().isNoContent());
        }
        notifications.create(authored);notifications.create(human);
        notifications.create(new NotificationRepository.CreateCommand(recipient,NotificationKind.SUPPORT_QUESTION_CREATED,null,
                "lowercase title","lowercase preview",null,"/app/inbox","announcement",announcement,server,null,null,null));
        var body=new java.util.LinkedHashMap<String,Object>();body.put("userId",recipient);body.put("kind","SUPPORT_QUESTION_CREATED");
        body.put("title","claimed title");body.put("bodyPreview","claimed body");body.put("href","/app/inbox");
        body.put("sourceType","ANNOUNCEMENT");body.put("sourceId",announcement);body.put("studyServerId",server);
        var delayed=new DurableEvent(UUID.randomUUID(),1,"community",100,"NOTIFICATION",
                NotificationEventWriter.aggregateKey(recipient,"ANNOUNCEMENT",announcement,"SUPPORT_QUESTION_CREATED"),mapper.writeValueAsString(body));
        http.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(delayed))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE source_id=?",Integer.class,announcement)).isZero();
        assertThat(jdbc.queryForMap("SELECT id,created_at,read_at,done_at FROM notifications WHERE id=?",humanRow.id())).isEqualTo(metadata);
        assertThat(jdbc.queryForObject("SELECT title FROM notifications WHERE id=?",String.class,humanRow.id())).isEqualTo("Question update");
        assertThat(jdbc.queryForObject("SELECT body_preview FROM notifications WHERE id=?",String.class,humanRow.id())).isNull();
    }

    @Test void sourceCleanupDoesNotLoseALaterHumanUpdateThatHasNeverReachedAnInbox() throws Exception {
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        var source=new JdbcTemplate(ds);var tx=new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        source.execute(com.chanter.common.events.DurableOutbox.SCHEMA);source.execute(com.chanter.common.events.DurableConsumer.SCHEMA);
        source.execute(TerminalReapplyStore.SCHEMA);
        source.execute("CREATE TABLE lifecycle_erased_content(target_kind VARCHAR(16),target_id UUID,revision BIGINT,event_id UUID,terminal_digest VARCHAR(64),source_kind VARCHAR(24),source_id UUID,PRIMARY KEY(target_kind,target_id,source_kind,source_id))");
        source.execute(com.chanter.common.lifecycle.ErasedContentDelivery.SCHEMA);
        var owner=new TerminalReapplyStore(source,tx,"message",e -> TerminalReapplyStore.Cleanup.PENDING);
        var beans=new org.springframework.beans.factory.support.DefaultListableBeanFactory();beans.registerSingleton("terminal",owner);
        var outbox=new com.chanter.common.events.DurableOutbox(source,tx,"message",java.time.Clock.systemUTC());
        var delivery=new com.chanter.common.lifecycle.ErasedContentDelivery("message",source,tx,outbox,mapper,beans.getBeanProvider(TerminalReapplyStore.class));
        UUID recipient=UUID.randomUUID(),question=UUID.randomUUID();
        var page=nextPage("ACCOUNT",UUID.randomUUID(),Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        var entry=page.entries().getFirst();terminal.reapply(page);
        tx.executeWithoutResult(s -> {
            owner.applyTerminal(entry);
            source.update("INSERT INTO lifecycle_erased_content(target_kind,target_id,revision,event_id,terminal_digest,source_kind,source_id) VALUES ('ACCOUNT',?,?,?,?,?,?)",
                    entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),"QUESTION_PREVIEW",question);
            delivery.start(entry);
        });
        // Another author replies after closure but before the old author's advance is delivered.
        tx.executeWithoutResult(s -> new NotificationEventWriter(outbox,mapper).append(java.util.Map.of("userId",recipient,"kind","SUPPORT_QUESTION_ANSWERED",
                "title","Later human answer","bodyPreview","later private body","href","/app/inbox","sourceType","SUPPORT_QUESTION","sourceId",question)));
        var advance=source.queryForObject("SELECT * FROM durable_outbox WHERE kind='ACCOUNT_CONTENT_ADVANCE'",(rs,n)->
                new DurableEvent(rs.getObject("id",UUID.class),1,"message",rs.getLong("revision"),rs.getString("kind"),rs.getString("aggregate_key"),rs.getString("payload")));
        delivery.accept(advance);
        var erase=source.queryForObject("SELECT * FROM durable_outbox WHERE kind='ACCOUNT_CONTENT_ERASE' AND destination='lifecycle-notification'",(rs,n)->
                new DurableEvent(rs.getObject("id",UUID.class),1,"message",rs.getLong("revision"),rs.getString("kind"),rs.getString("aggregate_key"),rs.getString("payload")));
        http.perform(post("/api/v1/internal/lifecycle/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(erase))).andExpect(status().isNoContent());
        assertThat(count(recipient)).isZero();
        var pending=source.queryForObject("SELECT * FROM durable_outbox WHERE kind='NOTIFICATION' AND status='PENDING'",(rs,n)->
                new DurableEvent(rs.getObject("id",UUID.class),1,"message",rs.getLong("revision"),rs.getString("kind"),rs.getString("aggregate_key"),rs.getString("payload")));
        http.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(pending))).andExpect(status().isNoContent());
        assertThat(count(recipient)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM notifications WHERE user_id=?",String.class,recipient)).isEqualTo("Question update");
        assertThat(jdbc.queryForObject("SELECT body_preview FROM notifications WHERE user_id=?",String.class,recipient)).isNull();
    }

    private com.chanter.notification.domain.Notification create(UUID user,UUID server,UUID resource) {
        return notifications.create(new NotificationRepository.CreateCommand(user,NotificationKind.SUPPORT_QUESTION_CREATED,null,
                "private title","private body",null,"/app/inbox","RESOURCE",resource,server,null,null,null));
    }
    private int count(UUID user) { return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=?",Integer.class,user); }
    private TerminalJournal.Page nextPage(String kind,UUID target,Instant now) {
        var before=terminal.receipt().authority(); long revision=before.revision()+1; UUID event=UUID.randomUUID();
        String digest=TerminalJournal.digest(revision,event,kind,target,now,before.digest());
        var entry=new TerminalJournal.Entry(revision,event,kind,target,"DELETE",now,TerminalJournal.RETENTION_POLICY,before.digest(),digest);
        var head=new TerminalJournal.Watermark(revision,digest);
        return new TerminalJournal.Page(2,before,head,List.of(entry),head);
    }
}
