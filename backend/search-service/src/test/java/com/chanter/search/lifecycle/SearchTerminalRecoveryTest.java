package com.chanter.search.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.DurableEvent;
import com.chanter.common.events.SearchChange;
import com.chanter.common.lifecycle.TerminalJournal;
import com.chanter.common.lifecycle.TerminalReapplyStore;
import com.chanter.search.domain.SearchDocumentType;
import com.chanter.search.infra.JdbcSearchIndexRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class SearchTerminalRecoveryTest {
    private static final String TOKEN="test-internal-service-token-for-search";
    @Autowired MockMvc http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcSearchIndexRepository index;
    @Autowired TerminalReapplyStore terminal;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.chanter.search.application.CommunityNavigationClient community;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.chanter.search.application.MediaCatalogClient media;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.chanter.search.application.MessageFaqClient message;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.chanter.search.application.SearchSourceClient sources;

    @Test void resourceReapplyErasesOnlyItsPayloadAndFencesDurableAndLegacyReplacement() throws Exception {
        UUID server=UUID.randomUUID(), resource=UUID.randomUUID(), other=UUID.randomUUID();
        var change=resource(server,resource);
        index.apply(change); index.apply(resource(server,other));
        var page=nextPage("RESOURCE",resource);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            terminal.reapply(page); assertThat(count(resource)).isZero(); status.setRollbackOnly();
        });
        assertThat(count(resource)).isEqualTo(1);
        assertThat(terminal.terminal("RESOURCE",resource)).isFalse();
        http.perform(post("/api/v1/internal/lifecycle/journal/reapply").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(page))).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/internal/lifecycle/journal/reapply").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(page)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("search"));
        assertThat(count(resource)).isZero(); assertThat(count(other)).isEqualTo(1);
        assertThat(terminal.reapply(page).authority()).isEqualTo(page.next());
        var event=new DurableEvent(UUID.randomUUID(),1,"media",11,"RESOURCE","RESOURCE:"+resource,mapper.writeValueAsString(change));
        for(int attempt=0;attempt<2;attempt++) http.perform(post("/api/v1/internal/events")
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(event))).andExpect(status().isNoContent());
        index.replaceStudyServerIndex(server,List.of(new JdbcSearchIndexRepository.IndexEntry(UUID.randomUUID(),server,
                change.courseId(),"course",SearchDocumentType.RESOURCE,resource,"stale title","stale payload",Instant.now())));
        assertThat(count(resource)).isZero();
        assertThat(jdbc.queryForObject("SELECT cleanup_state FROM lifecycle_terminal_targets WHERE target_id=?",String.class,resource))
                .isEqualTo("COMPLETE");
    }

    @Test void missingCanonicalOwnershipRemainsPendingAndAnOverlappingWriterCannotRestoreScopedRows() throws Exception {
        UUID server=UUID.randomUUID(), resource=UUID.randomUUID(), unscoped=UUID.randomUUID();
        index.apply(resource(server,resource)); index.apply(resource(null,unscoped));
        var page=nextPage("STUDY_SERVER",server);
        var applied=new CountDownLatch(1); var release=new CountDownLatch(1); var writerStarted=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var deletion=workers.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                terminal.reapply(page); applied.countDown();
                try { if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
                catch(InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
            }));
            assertThat(applied.await(5,TimeUnit.SECONDS)).isTrue();
            var write=workers.submit(() -> { writerStarted.countDown(); index.apply(resource(server,resource)); });
            assertThat(writerStarted.await(5,TimeUnit.SECONDS)).isTrue();
            release.countDown(); deletion.get(5,TimeUnit.SECONDS); write.get(5,TimeUnit.SECONDS);
        } finally { release.countDown(); }
        assertThat(count(resource)).isZero(); assertThat(count(unscoped)).isEqualTo(1);
        index.replaceStudyServerIndex(server,List.of(new JdbcSearchIndexRepository.IndexEntry(UUID.randomUUID(),server,
                UUID.randomUUID(),"course",SearchDocumentType.RESOURCE,resource,"stale","stale",Instant.now())));
        assertThat(count(resource)).isZero();
        assertThat(jdbc.queryForObject("SELECT cleanup_state FROM lifecycle_terminal_targets WHERE target_id=?",String.class,server))
                .isEqualTo("PENDING");
        UUID account=UUID.randomUUID(); terminal.reapply(nextPage("ACCOUNT",account));
        assertThat(jdbc.queryForObject("SELECT cleanup_state FROM lifecycle_terminal_targets WHERE target_id=?",String.class,account))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_account_tombstones WHERE account_id=?",Integer.class,account)).isEqualTo(1);
    }

    @Test void accountAuthoredContentErasureFencesClaimedEventsAndLegacyReplacement() throws Exception {
        UUID server=UUID.randomUUID(),id=UUID.randomUUID();
        var change=new SearchChange("ANNOUNCEMENT",id,server,null,null,null,null,"author title","author body","/app/servers",false);
        index.apply(change);
        var page=nextPage("ACCOUNT",UUID.randomUUID());terminal.reapply(page);
        var batch=new com.chanter.common.lifecycle.ErasedContent.Batch(page.entries().getFirst(),List.of(new com.chanter.common.lifecycle.ErasedContent.Ref("ANNOUNCEMENT",id)));
        var event=new DurableEvent(UUID.randomUUID(),1,"community",50,com.chanter.common.lifecycle.ErasedContent.ERASE,batch.key(UUID.randomUUID()),mapper.writeValueAsString(batch));
        http.perform(post("/api/v1/internal/lifecycle/events").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event))).andExpect(status().isUnauthorized());
        for(int attempt=0;attempt<2;attempt++) http.perform(post("/api/v1/internal/lifecycle/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event))).andExpect(status().isNoContent());
        var delayed=new DurableEvent(UUID.randomUUID(),1,"community",100,"ANNOUNCEMENT","ANNOUNCEMENT:"+id,mapper.writeValueAsString(change));
        http.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(delayed))).andExpect(status().isNoContent());
        index.apply(change);
        index.replaceStudyServerIndex(server,List.of(new JdbcSearchIndexRepository.IndexEntry(UUID.randomUUID(),server,null,"",SearchDocumentType.ANNOUNCEMENT,id,"late","late",Instant.now())));
        assertThat(count(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind='ACCOUNT_CONTENT_ERASED' AND aggregate_key=?",Integer.class,event.aggregateKey())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cleanup_state FROM lifecycle_terminal_targets WHERE target_id=?",String.class,page.entries().getFirst().targetId())).isEqualTo("PENDING");
    }

    private SearchChange resource(UUID server,UUID resource) {
        return new SearchChange("RESOURCE",resource,server,UUID.randomUUID(),null,null,null,"private title","private text","/app/courses",false);
    }
    private int count(UUID id) { return jdbc.queryForObject("SELECT COUNT(*) FROM search_index_entries WHERE source_id=?",Integer.class,id); }
    private TerminalJournal.Page nextPage(String kind,UUID target) {
        var before=terminal.receipt().authority(); long revision=before.revision()+1; UUID event=UUID.randomUUID();
        Instant now=Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String digest=TerminalJournal.digest(revision,event,kind,target,now,before.digest());
        var entry=new TerminalJournal.Entry(revision,event,kind,target,"DELETE",now,TerminalJournal.RETENTION_POLICY,before.digest(),digest);
        var head=new TerminalJournal.Watermark(revision,digest);
        return new TerminalJournal.Page(2,before,head,List.of(entry),head);
    }
}
