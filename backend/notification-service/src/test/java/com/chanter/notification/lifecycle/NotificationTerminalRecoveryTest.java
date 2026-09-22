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
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.chanter.notification.application.NotificationVisibility visibility;

    @Test void actualNotificationsExportsAndPermanentFenceCommitTogetherAndDelayedEventsCannotRestoreThem() throws Exception {
        UUID owner=UUID.randomUUID(), other=UUID.randomUUID(), server=UUID.randomUUID(), resource=UUID.randomUUID();
        var own=create(owner,server,resource);
        var retained=create(other,UUID.randomUUID(),UUID.randomUUID());
        Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var request=new ExportSnapshotStore.Request(UUID.randomUUID(),owner,now,now.plusSeconds(86400));
        snapshots.capture(request,output -> new NotificationAccountExport(jdbc).capture(owner,output));
        var page=nextPage("ACCOUNT",owner,now);
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
                .andExpect(status().isOk()).andExpect(jsonPath("$.pendingTargets").value(0));
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
