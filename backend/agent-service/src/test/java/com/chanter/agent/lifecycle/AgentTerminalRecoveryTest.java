package com.chanter.agent.lifecycle;

import static org.assertj.core.api.Assertions.*;
import com.chanter.agent.application.*;
import com.chanter.agent.domain.*;
import com.chanter.agent.infra.NativeRequestRepository;
import com.chanter.agent.config.LlmProperties.Model;
import com.chanter.common.lifecycle.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties={"chanter.events.dispatch-enabled=false","chanter.ingestion.worker-enabled=false"})
@ActiveProfiles("test")
class AgentTerminalRecoveryTest {
    @Autowired TerminalReapplyStore terminal;
    @Autowired DeletedScopeStore scope;
    @Autowired ResourceIngestionService ingestion;
    @Autowired ResourceChunkRepository chunks;
    @Autowired StudyAssistantAnswerRepository answers;
    @Autowired NativeRequestRepository nativeRequests;
    @Autowired AiGenerationLedger ledger;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ExportSnapshotStore snapshots;
    @Autowired StudyAssistantAnswerPersistenceService persistence;
    @Autowired com.chanter.common.events.DurableOutbox outbox;
    @Autowired AnswerRetractions retractions;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Autowired AccountDeletionParticipant participant;
    @Autowired AccountDeletionProtocol protocol;
    private final Model model=new Model("Fixture","ollama","fixture",null,null,64,16,Duration.ofSeconds(3),Set.of(),null);

    @Test void statusRepairRemembersTheAnswerAfterDeliveredPayloadErasure() {
        UUID server=UUID.randomUUID(),user=UUID.randomUUID(); install(server,user);
        var answer=new StudyAssistantAnswer(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),server,user,"question","answer",AnswerConfidence.HIGH,false,List.of(),Instant.now());
        answers.saveAnswer(answer,InvocationType.GROUNDED_ANSWER); persistence.reconcileAnswerStatus(answer);
        jdbc.update("UPDATE durable_outbox SET status='DELIVERED',payload='{}' WHERE aggregate_key=?","ACCEPTED_ANSWER:"+answer.supportQuestionId());
        persistence.reconcileAnswerStatus(answer);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE aggregate_key=?",Integer.class,"ACCEPTED_ANSWER:"+answer.supportQuestionId())).isEqualTo(1);
    }

    @Test void resourceReapplyErasesAllDerivedPayloadAndTemporaryCopiesButKeepsUnknownAccounting() throws Exception {
        UUID server=UUID.randomUUID(),user=UUID.randomUUID(),course=UUID.randomUUID(),resource=UUID.randomUUID(),question=UUID.randomUUID(),channel=UUID.randomUUID();
        install(server,user);
        ingestion.ingest(course,resource,"source.txt","private approved evidence".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var answer=new StudyAssistantAnswer(UUID.randomUUID(),question,channel,server,user,"private question","private answer",AnswerConfidence.HIGH,false,
                List.of(new StudyAssistantAnswerSource(UUID.randomUUID(),resource,"source.txt","private approved evidence")),Instant.now());
        answers.saveAnswer(answer,InvocationType.GROUNDED_ANSWER);
        persistence.reconcileAnswerStatus(answer);
        var available=jdbc.queryForObject("SELECT available_at FROM durable_outbox WHERE aggregate_key=? AND kind='ACCEPTED_ANSWER'",java.sql.Timestamp.class,
                "ACCEPTED_ANSWER:"+question).toInstant();
        var claimOutbox=new com.chanter.common.events.DurableOutbox(jdbc,new TransactionTemplate(transactions),"agent",Clock.fixed(available.plusSeconds(1),ZoneOffset.UTC));
        var claimed=claimOutbox.claim().orElseThrow();
        assertThat(claimed.event().kind()).isEqualTo(com.chanter.common.events.AcceptedAnswerStatus.KIND);
        UUID reservation=ledger.reserve(server,question,user,"fixture",model);
        String evidence="{\"studyServerId\":\""+server+"\",\"courseId\":\""+course+"\",\"question\":\"private question\",\"citations\":[{\"resourceId\":\""+resource+"\",\"resourceTitle\":\"source.txt\",\"excerpt\":\"private approved evidence\"}]}";
        var request=new NativeRequestRepository.Request(reservation,channel,question,user,UUID.randomUUID(),UUID.randomUUID(),"fixture",evidence,"a".repeat(64),"b".repeat(64),Instant.now().plusSeconds(120));
        nativeRequests.issue(request);
        UUID export=UUID.randomUUID(); Instant now=Instant.now();
        snapshots.capture(new ExportSnapshotStore.Request(export,user,now,now.plusSeconds(3600)),output -> output.jsonLines("fixture",rows -> rows.add(Map.of("content","private copy"))));
        var entry=entry("RESOURCE",resource);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            terminal.applyTerminal(entry); assertThat(chunks.findByResourceId(resource)).isEmpty(); status.setRollbackOnly();
        });
        assertThat(chunks.findByResourceId(resource)).isNotEmpty(); assertThat(answers.findBySupportQuestionId(question)).isPresent();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_pages WHERE snapshot_id=?",Integer.class,export)).isPositive();
        var command=new com.chanter.common.events.DurableEvent(UUID.randomUUID(),1,"auth",entry.revision(),AccountDeletionProtocol.TERMINAL,
                AccountDeletionProtocol.key("RESOURCE",resource),protocol.encode(new AccountDeletionProtocol.Terminal(UUID.randomUUID(),entry)));
        participant.accept(command); participant.accept(command);
        assertThat(chunks.findByResourceId(resource)).isEmpty(); assertThat(answers.findBySupportQuestionId(question)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource_chunk_embeddings WHERE resource_id=?",Integer.class,resource)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_pages WHERE snapshot_id=?",Integer.class,export)).isZero();
        assertThat(jdbc.queryForObject("SELECT outcome FROM native_companion_requests WHERE id=?",String.class,reservation)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT evidence_json FROM native_companion_requests WHERE id=?",String.class,reservation)).isNull();
        assertThatThrownBy(() -> nativeRequests.claim(reservation,channel,question,user,request.session(),request.installation())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> answers.saveAnswer(answer,InvocationType.GROUNDED_ANSWER)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ingestion.ingest(course,resource,"late.txt",new byte[]{1})).isInstanceOf(ResponseStatusException.class);
        ledger.settle(reservation,LlmUsage.UNKNOWN,"CANCELLED",0,"fixture",null,model,true);
        assertThat(ledger.summary(server).accountedTokens()).isEqualTo(80);
        assertThat(ledger.summary(server).unknownUsageCount()).isEqualTo(1);
        var cleanup=new TransactionTemplate(transactions).execute(status -> terminal.cleanup(entry));
        assertThat(cleanup).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
        var emitted=jdbc.queryForObject("SELECT payload FROM durable_outbox WHERE kind='ANSWER_RETRACTED' AND aggregate_key=?",String.class,
                com.chanter.common.events.AcceptedAnswerStatus.KIND+":"+question);
        var change=mapper.readValue(emitted,com.chanter.common.events.AnswerRetraction.class);
        assertThat(change.answerId()).isEqualTo(answer.id());
        assertThat(jdbc.queryForObject("SELECT revision FROM durable_outbox WHERE kind='ANSWER_RETRACTED' AND aggregate_key=?",Long.class,change.aggregateKey()))
                .isGreaterThan(claimed.event().revision());
        assertThat(claimed.event().payload()).contains(answer.id().toString()); // A claimed old payload remains deliverable; ordering must defeat it downstream.
        jdbc.execute("ALTER TABLE durable_outbox ADD CONSTRAINT fixture_completion_failure CHECK(NOT(kind='ACCOUNT_DELETE_RECEIPT' AND payload LIKE '%\"state\":\"COMPLETE\"%'))");
        try { assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status ->
                retractions.acknowledge(new com.chanter.common.events.AnswerReconciliation(change,"COMPLETE"))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
        finally { jdbc.execute("ALTER TABLE durable_outbox DROP CONSTRAINT fixture_completion_failure"); }
        assertThat(jdbc.queryForObject("SELECT receipt_state FROM lifecycle_answer_retractions WHERE answer_id=?",String.class,answer.id())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT cleanup_state FROM lifecycle_terminal_targets WHERE target_kind='RESOURCE' AND target_id=?",String.class,resource)).isEqualTo("PENDING");
        new TransactionTemplate(transactions).executeWithoutResult(status -> retractions.acknowledge(new com.chanter.common.events.AnswerReconciliation(change,"COMPLETE")));
        TerminalReapplyStore.Cleanup completed=new TransactionTemplate(transactions).execute(status -> terminal.cleanup(entry));
        assertThat(completed).isEqualTo(TerminalReapplyStore.Cleanup.COMPLETE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind=? AND aggregate_key=?",Integer.class,
                AccountDeletionProtocol.RECEIPT,command.aggregateKey())).isEqualTo(2); // PENDING, then downstream-proven COMPLETE.
    }

    @Test void legacyCourseIndexWaitsForVerifiedScopeThenRejectsFutureUnknownResourceIds() {
        UUID server=UUID.randomUUID(),course=UUID.randomUUID(),resource=UUID.randomUUID();
        ingestion.ingest(course,resource,"legacy.txt","private legacy evidence".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var entry=entry("STUDY_SERVER",server); apply(entry);
        assertThat(chunks.findByResourceId(resource)).isNotEmpty();
        scope.accept(new DeletedScope.Import(entry,page(entry,"COURSE",List.of(course))));
        scope.accept(new DeletedScope.Import(entry,page(entry,"CHANNEL",List.of())));
        assertThat(chunks.findByResourceId(resource)).isEmpty();
        assertThatThrownBy(() -> ingestion.ingest(course,UUID.randomUUID(),"late.txt",new byte[]{1})).isInstanceOf(ResponseStatusException.class);
        var cleanup=new TransactionTemplate(transactions).execute(status -> terminal.cleanup(entry));
        assertThat(cleanup).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
    }

    @Test void committedAccountFenceRejectsWaitingReservationWhileExistingSettlementRemainsConservative() throws Exception {
        UUID server=UUID.randomUUID(),user=UUID.randomUUID(); install(server,user);
        UUID reservation=ledger.reserve(server,UUID.randomUUID(),user,"fixture",model);
        var entry=entry("ACCOUNT",user); var fenced=new CountDownLatch(1); var release=new CountDownLatch(1); var starting=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(3)) {
            var deletion=pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                terminal.applyTerminal(entry); fenced.countDown(); await(release);
            }));
            await(fenced);
            var late=pool.submit(() -> { starting.countDown(); return ledger.reserve(server,UUID.randomUUID(),user,"fixture",model); });
            await(starting);
            var settling=pool.submit(() -> ledger.settle(reservation,LlmUsage.UNKNOWN,"CANCELLED",0,"fixture",null,model,true));
            settling.get(5,TimeUnit.SECONDS);
            assertThatThrownBy(() -> late.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); deletion.get(5,TimeUnit.SECONDS);
            assertThatThrownBy(() -> late.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(ResponseStatusException.class);
        } finally { release.countDown(); }
        assertThat(ledger.summary(server).requestCount()).isEqualTo(1);
        assertThat(ledger.summary(server).accountedTokens()).isEqualTo(80);
        assertThat(ledger.summary(server).unknownUsageCount()).isEqualTo(1);
    }
    private void install(UUID server,UUID user) {
        jdbc.update("INSERT INTO study_assistant_installs VALUES (?,?,?,?)",UUID.randomUUID(),server,user,java.sql.Timestamp.from(Instant.now()));
    }
    private void apply(TerminalJournal.Entry entry) { new TransactionTemplate(transactions).executeWithoutResult(status -> terminal.applyTerminal(entry)); }
    private TerminalJournal.Entry entry(String kind,UUID target) {
        long revision=jdbc.queryForObject("SELECT COALESCE(MAX(revision),0)+1 FROM lifecycle_terminal_targets",Long.class);
        UUID event=UUID.randomUUID(); Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        return new TerminalJournal.Entry(revision,event,kind,target,"DELETE",now,TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,
                TerminalJournal.digest(revision,event,kind,target,now,TerminalJournal.GENESIS));
    }
    private static DeletedScope.Page page(TerminalJournal.Entry entry,String kind,List<UUID> ids) {
        String digest=DeletedScope.startDigest(entry.digest(),kind,ids.size()); for(UUID id:ids) digest=DeletedScope.nextDigest(digest,id);
        return new DeletedScope.Page(1,entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),kind,DeletedScope.START,ids.size(),digest,ids,null);
    }
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
        catch(InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
    }
}
