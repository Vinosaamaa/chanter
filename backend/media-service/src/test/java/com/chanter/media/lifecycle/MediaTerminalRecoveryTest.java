package com.chanter.media.lifecycle;

import static org.assertj.core.api.Assertions.*;
import com.chanter.common.lifecycle.*;
import com.chanter.media.application.ResourceLifecycle;
import com.chanter.media.domain.CourseResource;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:media-terminal;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "chanter.media.worker-enabled=false","chanter.events.dispatch-enabled=false","chanter.recovery-mode=true",
        "chanter.recovery-restore-id=33333333-3333-4333-8333-333333333333"})
@ActiveProfiles("test")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class MediaTerminalRecoveryTest {
    @Autowired ResourceLifecycle resources;
    @Autowired com.chanter.media.application.CourseResourceService service;
    @Autowired TerminalReapplyStore terminal;
    @Autowired DeletedScopeStore current;
    @Autowired RecoveryScopeStore historical;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    @Test void exactAgentDeletionReceiptIsAuthenticatedAtomicAndCannotStandInForPhysicalClosure() throws Exception {
        var resource=resources.reserve(candidate(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()));
        var entry=entry("RESOURCE",resource.id());apply(entry);
        UUID command=jdbc.queryForObject("SELECT deletion_event_id FROM course_resources WHERE id=?",UUID.class,resource.id());
        var receipt=new com.chanter.common.events.ResourceDeletionReceipt(resource.id(),command);
        var event=new com.chanter.common.events.DurableEvent(UUID.randomUUID(),1,"agent",77,com.chanter.common.events.ResourceDeletionReceipt.KIND,receipt.key(),mapper.writeValueAsString(receipt));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/internal/events").contentType("application/json").content(mapper.writeValueAsBytes(event)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        var wrong=new com.chanter.common.events.ResourceDeletionReceipt(resource.id(),UUID.randomUUID());
        var forged=new com.chanter.common.events.DurableEvent(UUID.randomUUID(),1,"agent",78,event.kind(),wrong.key(),mapper.writeValueAsString(wrong));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/internal/events")
                .header(com.chanter.common.auth.AuthHeaders.INTERNAL_SERVICE_TOKEN,"test-internal-service-token-for-media")
                .contentType("application/json").content(mapper.writeValueAsBytes(forged))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        jdbc.execute("ALTER TABLE course_resources ADD CONSTRAINT fail_delete_receipt CHECK(deletion_reconciled=FALSE)");
        try {assertThatThrownBy(() -> mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/internal/events")
                .header(com.chanter.common.auth.AuthHeaders.INTERNAL_SERVICE_TOKEN,"test-internal-service-token-for-media")
                .contentType("application/json").content(mapper.writeValueAsBytes(event)))).hasRootCauseInstanceOf(org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class);}
        finally {jdbc.execute("ALTER TABLE course_resources DROP CONSTRAINT fail_delete_receipt");}
        assertThat(jdbc.queryForObject("SELECT deletion_reconciled FROM course_resources WHERE id=?",Boolean.class,resource.id())).isFalse();
        for(int retry=0;retry<2;retry++) mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/internal/events")
                .header(com.chanter.common.auth.AuthHeaders.INTERNAL_SERVICE_TOKEN,"test-internal-service-token-for-media")
                .contentType("application/json").content(mapper.writeValueAsBytes(event))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deletion_reconciled FROM course_resources WHERE id=?",Boolean.class,resource.id())).isTrue();
        assertThat(resources.find(resource.id()).orElseThrow().state()).isEqualTo("DELETE_PENDING");
        assertThat(new TransactionTemplate(transactions).<TerminalReapplyStore.Cleanup>execute(s -> terminal.cleanup(entry))).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
    }

    @org.junit.jupiter.api.BeforeEach void resetResources() {
        jdbc.update("DELETE FROM course_resources");
        jdbc.update("UPDATE media_storage_budget SET reserved_bytes=0");
    }
    @Test void verifiedMaintenanceAccountingRequiresAnExactSettledTerminalTupleAndCommitsOnlyOnce() {
        var resource=resources.reserve(candidate(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()));
        var proof=new ResourceLifecycle.MaintenanceDeletion(resource.id(),resource.storageBackend(),resource.storageKey(),null,resource.byteSize(),resource.sha256());
        var tx=new TransactionTemplate(transactions);
        assertThatThrownBy(() -> resources.finishVerifiedMaintenanceDelete(proof))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> resources.finishVerifiedMaintenanceDelete(proof))).isInstanceOf(IllegalStateException.class);
        var entry=entry("RESOURCE",resource.id());apply(entry);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> resources.finishVerifiedMaintenanceDelete(proof))).hasMessageContaining("settled terminal source");
        resources.storageWriteSettled(resource.id());
        var claimed=resources.claim(false).orElseThrow();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> resources.finishVerifiedMaintenanceDelete(proof))).hasMessageContaining("settled terminal source");
        resources.retryJob(resource.id(),claimed.leaseId());
        var stale=new ResourceLifecycle.MaintenanceDeletion(resource.id(),resource.storageBackend(),resource.storageKey(),"unverified-migration-key",resource.byteSize(),resource.sha256());
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> resources.finishVerifiedMaintenanceDelete(stale))).hasMessageContaining("settled terminal source");
        jdbc.execute("ALTER TABLE media_storage_budget ADD CONSTRAINT fail_release CHECK(reserved_bytes>=10)");
        try { assertThatThrownBy(() -> tx.executeWithoutResult(s -> resources.finishVerifiedMaintenanceDelete(proof))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
        finally { jdbc.execute("ALTER TABLE media_storage_budget DROP CONSTRAINT fail_release"); }
        assertThat(resources.find(resource.id()).orElseThrow().state()).isEqualTo("DELETE_PENDING");
        assertThat(resources.courseUsage(resource.courseId()).reservedBytes()).isEqualTo(10);
        tx.executeWithoutResult(s -> resources.finishVerifiedMaintenanceDelete(proof));
        tx.executeWithoutResult(s -> resources.finishVerifiedMaintenanceDelete(proof));
        assertThat(resources.find(resource.id()).orElseThrow().state()).isEqualTo("DELETED");
        assertThat(resources.courseUsage(resource.courseId()).reservedBytes()).isZero();
        TerminalReapplyStore.Cleanup remaining=tx.execute(s -> terminal.cleanup(entry));
        assertThat(remaining).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
    }
    @Test void unknownUploadCannotReleaseBytesAndAccountFenceRejectsAnotherReservation() {
        var resource=resources.reserve(candidate(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()));
        var entry=entry("ACCOUNT",resource.uploadedByUserId());
        new TransactionTemplate(transactions).executeWithoutResult(status -> { terminal.applyTerminal(entry); status.setRollbackOnly(); });
        assertThat(resources.find(resource.id()).orElseThrow().state()).isEqualTo("STAGING");
        assertThat(resources.terminalScope(resource)).isFalse();
        apply(entry); apply(entry);
        assertThat(resources.find(resource.id()).orElseThrow().state()).isEqualTo("DELETE_PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_erased_content WHERE target_kind='ACCOUNT' AND target_id=? AND source_kind='RESOURCE' AND source_id=?",Integer.class,
                resource.uploadedByUserId(),resource.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind='ACCOUNT_CONTENT_ADVANCE' AND aggregate_key=?",Integer.class,"ACCOUNT_CONTENT_ADVANCE:"+entry.eventId())).isEqualTo(1);
        assertThat(resources.terminalScope(resource)).isTrue();
        assertThat(resources.claim(false)).isEmpty();
        assertThat(resources.courseUsage(resource.courseId()).reservedBytes()).isEqualTo(10);
        assertThatThrownBy(() -> resources.reserve(candidate(resource.uploadedByUserId(),resource.courseId(),resource.studyServerId())))
                .isInstanceOf(ResponseStatusException.class);
        // A missing object or expired lease has supplied no settlement proof.
        jdbc.update("UPDATE course_resources SET lease_until=CURRENT_TIMESTAMP-INTERVAL '1' DAY WHERE id=?",resource.id());
        assertThat(resources.claim(false)).isEmpty();
        var cleanup=new TransactionTemplate(transactions).execute(status -> terminal.cleanup(entry));
        assertThat(cleanup).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
    }
    @Test void laterCleanupPagesAreAlreadyUnreadableAndUseTheExistingWorkerQueue() {
        UUID user=UUID.randomUUID(),course=UUID.randomUUID(),server=UUID.randomUUID();
        for(int index=0;index<257;index++) resources.reserve(candidate(user,course,server));
        var entry=entry("ACCOUNT",user); apply(entry);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_resources WHERE state='DELETE_PENDING'",Integer.class)).isEqualTo(256);
        UUID later=jdbc.queryForObject("SELECT id FROM course_resources WHERE state='STAGING'",UUID.class);
        assertThat(resources.terminalScope(resources.find(later).orElseThrow())).isTrue();
        assertThatThrownBy(() -> service.getCourseResource(later,UUID.randomUUID())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThat(resources.claim(false)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_resources WHERE state='DELETE_PENDING'",Integer.class)).isEqualTo(257);
        assertThat(resources.courseUsage(course).reservedBytes()).isEqualTo(2570);
    }
    @Test void recoveryPreservesUnknownNamespaceAndOnlyAcceptsAnExistingMatchingBinding() throws Exception {
        jdbc.update("UPDATE media_storage_budget SET storage_namespace=NULL WHERE id=1");
        resources.bindNamespace("fixture-original");
        assertThat(jdbc.queryForObject("SELECT storage_namespace FROM media_storage_budget WHERE id=1",String.class)).isNull();
        String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest("fixture-original".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("UPDATE media_storage_budget SET storage_namespace=? WHERE id=1",digest);
        resources.bindNamespace("fixture-original");
        assertThatThrownBy(() -> resources.bindNamespace("fixture-changed")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT storage_namespace FROM media_storage_budget WHERE id=1",String.class)).isEqualTo(digest);
    }
    @Test void terminalServerBlocksInFlightScannerPublicationAndPreservesPhysicalCleanupRequirement() {
        var resource=resources.reserve(candidate(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()));
        resources.quarantine(resource.id());
        var scanning=resources.claim(false).orElseThrow();
        var entry=entry("STUDY_SERVER",resource.studyServerId()); apply(entry);
        assertThat(resources.finishScan(resource.id(),scanning.leaseId(),"AVAILABLE")).isFalse();
        var deletion=resources.claim(false).orElseThrow();
        assertThat(deletion.operation()).isEqualTo("DELETE");
        assertThat(resources.courseUsage(resource.courseId()).reservedBytes()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE aggregate_key=? AND destination IN ('search','agent')",Integer.class,"RESOURCE:"+resource.id())).isGreaterThanOrEqualTo(1);
        var cleanup=new TransactionTemplate(transactions).execute(status -> terminal.cleanup(entry));
        assertThat(cleanup).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
    }
    @Test void verifiedHistoricalScopeQueuesLegacyCleanupWithoutUsingEmptyRecoveryScratchAsErasureProof() {
        UUID server=UUID.randomUUID(),course=UUID.randomUUID(),older=UUID.randomUUID();
        var resource=resources.reserve(candidate(UUID.randomUUID(),older,null));
        resources.quarantine(resource.id());
        var entry=entry("STUDY_SERVER",server); apply(entry);
        current.accept(new DeletedScope.Import(entry,page(entry,"COURSE",List.of(course),entry.digest())));
        current.accept(new DeletedScope.Import(entry,page(entry,"CHANNEL",List.of(),entry.digest())));
        assertThat(resources.find(resource.id()).orElseThrow().state()).isEqualTo("QUARANTINED");
        UUID operation=UUID.randomUUID();
        var union=java.util.stream.Stream.of(course,older).sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        historical.accept(new RecoveryScope.Import(historical.restoreId(),operation,current.scopeDigest(entry,"COURSE"),entry,
                page(entry,"COURSE",union,historical.basis(entry,"COURSE"))));
        var last=new RecoveryScope.Import(historical.restoreId(),operation,current.scopeDigest(entry,"CHANNEL"),entry,
                page(entry,"CHANNEL",List.of(),historical.basis(entry,"CHANNEL")));
        new TransactionTemplate(transactions).executeWithoutResult(status -> { historical.accept(last); status.setRollbackOnly(); });
        assertThat(resources.find(resource.id()).orElseThrow().state()).isEqualTo("QUARANTINED");
        historical.accept(last); historical.accept(last);
        assertThat(resources.find(resource.id()).orElseThrow().state()).isEqualTo("DELETE_PENDING");
        assertThat(resources.courseUsage(older).reservedBytes()).isEqualTo(10);
        assertThatThrownBy(() -> resources.reserve(candidate(UUID.randomUUID(),older,null))).isInstanceOf(ResponseStatusException.class);
        var cleanup=new TransactionTemplate(transactions).execute(status -> terminal.cleanup(entry));
        assertThat(cleanup).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
    }
    private CourseResource candidate(UUID user,UUID course,UUID server) {
        UUID id=UUID.randomUUID();
        return new CourseResource(id,course,"Private title","source.txt","text/plain",10,"resources/v1/"+course+"/"+id+"/"+UUID.randomUUID(),
                true,user,Instant.now(),"STAGING","a".repeat(64),UUID.randomUUID(),"local","NONE",Set.of(),server,null);
    }
    private void apply(TerminalJournal.Entry entry) { new TransactionTemplate(transactions).executeWithoutResult(status -> terminal.applyTerminal(entry)); }
    private TerminalJournal.Entry entry(String kind,UUID target) {
        long revision=jdbc.queryForObject("SELECT COALESCE(MAX(revision),0)+1 FROM lifecycle_terminal_targets",Long.class);
        UUID event=UUID.randomUUID(); Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        return new TerminalJournal.Entry(revision,event,kind,target,"DELETE",now,TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,
                TerminalJournal.digest(revision,event,kind,target,now,TerminalJournal.GENESIS));
    }
    private static DeletedScope.Page page(TerminalJournal.Entry entry,String kind,List<UUID> ids,String basis) {
        String digest=DeletedScope.startDigest(basis,kind,ids.size()); for(UUID id:ids) digest=DeletedScope.nextDigest(digest,id);
        return new DeletedScope.Page(1,entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),kind,DeletedScope.START,ids.size(),digest,ids,null);
    }
}
