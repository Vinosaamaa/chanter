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

@SpringBootTest(properties={"chanter.events.dispatch-enabled=false","chanter.ingestion.worker-enabled=false",
        "spring.datasource.url=jdbc:h2:mem:agent-terminal-recovery;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
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
    @Autowired ErasedContentDelivery content;
    @Autowired org.springframework.context.ConfigurableApplicationContext context;
    @Autowired com.chanter.agent.infra.TestSupportQuestionChannelAccessClient channelAccess;
    @Autowired com.chanter.agent.infra.TestStudyAssistantGrantCandidatesClient grantCandidates;
    private final Model model=new Model("Fixture","ollama","fixture",null,null,64,16,Duration.ofSeconds(3),Set.of(),null);

    @Test void serverDispositionRequiresScopeAndExactAnswerReceiptWhileKeepingConservativeClaims() throws Exception {
        UUID server=UUID.randomUUID(),user=UUID.randomUUID(),question=UUID.randomUUID(),channel=UUID.randomUUID(),course=UUID.randomUUID();install(server,user);
        UUID reservation=ledger.reserve(server,question,user,"server-fixture",model);
        ledger.settle(reservation,LlmUsage.UNKNOWN,"UNKNOWN",0,"fixture","private-provider-id",model,true);
        var request=new NativeRequestRepository.Request(reservation,channel,question,user,UUID.randomUUID(),UUID.randomUUID(),"fixture",
                mapper.writeValueAsString(Map.of("studyServerId",server,"courseId",course,"question","private question","citations",List.of())),"a".repeat(64),"b".repeat(64),Instant.now().plusSeconds(120));
        nativeRequests.issue(request);
        var answer=new StudyAssistantAnswer(UUID.randomUUID(),question,channel,server,user,"private question","private answer",AnswerConfidence.HIGH,false,List.of(),Instant.now());
        answers.saveAnswer(answer,InvocationType.GROUNDED_ANSWER);
        var entry=entry("STUDY_SERVER",server);apply(entry);var tx=new TransactionTemplate(transactions);
        assertThat(tx.<TerminalReapplyStore.Cleanup>execute(s -> terminal.cleanup(entry))).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
        scope.accept(new DeletedScope.Import(entry,page(entry,"COURSE",List.of(course))));
        var channels=new DeletedScope.Import(entry,page(entry,"CHANNEL",List.of(channel)));
        tx.executeWithoutResult(s -> {scope.accept(channels);s.setRollbackOnly();});
        assertThat(jdbc.queryForObject("SELECT learner_user_id FROM ai_generation_usage WHERE id=?",UUID.class,reservation)).isEqualTo(user);
        scope.accept(channels);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM native_companion_requests WHERE id=?",Integer.class,reservation)).isZero();
        assertThat(jdbc.queryForObject("SELECT learner_user_id FROM ai_generation_usage WHERE id=?",UUID.class,reservation)).isNull();
        assertThat(jdbc.queryForObject("SELECT provider_request_id FROM ai_generation_usage WHERE id=?",String.class,reservation)).isNull();
        assertThat(tx.<TerminalReapplyStore.Cleanup>execute(s -> terminal.cleanup(entry))).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
        ledger.settle(reservation,new LlmUsage(0,0,0,0,0),"CANCELLED",0,"fixture","late-provider-id",model,false);
        assertThat(ledger.summary(server).accountedTokens()).isEqualTo(80);assertThat(ledger.summary(server).unknownUsageCount()).isEqualTo(1);
        var receipt=new com.chanter.common.events.AnswerReconciliation(new com.chanter.common.events.AnswerRetraction(answer.id(),question,channel,user),"COMPLETE");
        tx.executeWithoutResult(s -> retractions.acknowledge(receipt));
        assertThat(jdbc.queryForObject("SELECT cleanup_state FROM lifecycle_terminal_targets WHERE target_kind='STUDY_SERVER' AND target_id=?",String.class,server)).isEqualTo("PRESERVED");
        tx.executeWithoutResult(s -> retractions.acknowledge(receipt));
        assertThatThrownBy(() -> ledger.reserve(server,UUID.randomUUID(),user,"late",model)).isInstanceOf(ResponseStatusException.class);
        var empty=entry("STUDY_SERVER",UUID.randomUUID());apply(empty);
        scope.accept(new DeletedScope.Import(empty,page(empty,"COURSE",List.of())));scope.accept(new DeletedScope.Import(empty,page(empty,"CHANNEL",List.of())));
        assertThat(tx.<TerminalReapplyStore.Cleanup>execute(s -> terminal.cleanup(empty))).isEqualTo(TerminalReapplyStore.Cleanup.COMPLETE);
    }

    @Test void accountAttributionErasurePreservesUnknownClaimsWithoutLateRefundAndWaitsForFinals() throws Exception {
        UUID server=UUID.randomUUID(),user=UUID.randomUUID(),question=UUID.randomUUID(),channel=UUID.randomUUID(),course=UUID.randomUUID();install(server,user);
        var nativeModel=new Model("Native fixture","codex-native","fixture",null,null,64,16,Duration.ofSeconds(3),Set.of(),null);
        UUID reservation=ledger.reserve(server,question,user,"native-fixture",nativeModel);
        ledger.settle(reservation,LlmUsage.UNKNOWN,"UNKNOWN",0,"fixture","provider-record",nativeModel,true);
        var evidence=mapper.writeValueAsString(Map.of("studyServerId",server,"courseId",course,"question","private pending question","citations",List.of()));
        var request=new NativeRequestRepository.Request(reservation,channel,question,user,UUID.randomUUID(),UUID.randomUUID(),"fixture",evidence,"a".repeat(64),"b".repeat(64),Instant.now().plusSeconds(120));
        nativeRequests.issue(request);var entry=entry("ACCOUNT",user);var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(s -> {terminal.applyTerminal(entry);s.setRollbackOnly();});
        assertThat(jdbc.queryForObject("SELECT learner_user_id FROM ai_generation_usage WHERE id=?",UUID.class,reservation)).isEqualTo(user);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM native_companion_requests WHERE id=?",Integer.class,reservation)).isEqualTo(1);
        apply(entry);apply(entry);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM native_companion_requests WHERE id=?",Integer.class,reservation)).isZero();
        assertThat(jdbc.queryForObject("SELECT learner_user_id FROM ai_generation_usage WHERE id=?",UUID.class,reservation)).isNull();
        assertThat(jdbc.queryForObject("SELECT provider_request_id FROM ai_generation_usage WHERE id=?",String.class,reservation)).isNull();
        assertThat(jdbc.queryForObject("SELECT installed_by_user_id FROM study_assistant_installs WHERE study_server_id=?",UUID.class,server)).isNull();
        ledger.settle(reservation,new LlmUsage(0,0,0,0,0),"CANCELLED",0,"fixture","late-provider-record",nativeModel,false);
        assertThat(ledger.summary(server).accountedTokens()).isEqualTo(80);assertThat(ledger.summary(server).unknownUsageCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT outcome FROM ai_generation_usage WHERE id=?",String.class,reservation)).isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject("SELECT provider_request_id FROM ai_generation_usage WHERE id=?",String.class,reservation)).isNull();
        assertThatThrownBy(() -> nativeRequests.claim(reservation,channel,question,user,request.session(),request.installation())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ledger.reserve(server,question,UUID.randomUUID(),"fixture",model)).isInstanceOf(AiGenerationLedger.AttemptConflict.class);
        assertThat(tx.<TerminalReapplyStore.Cleanup>execute(s -> terminal.cleanup(entry))).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
        acknowledgeEmptyFinals(entry);
        assertThat(tx.<TerminalReapplyStore.Cleanup>execute(s -> terminal.cleanup(entry))).isEqualTo(TerminalReapplyStore.Cleanup.PRESERVED);
        var empty=entry("ACCOUNT",UUID.randomUUID());apply(empty);acknowledgeEmptyFinals(empty);
        assertThat(tx.<TerminalReapplyStore.Cleanup>execute(s -> terminal.cleanup(empty))).isEqualTo(TerminalReapplyStore.Cleanup.COMPLETE);
    }

    private void acknowledgeEmptyFinals(TerminalJournal.Entry entry) throws Exception {
        acknowledgeFinals(entry,0);
    }
    private void acknowledgeFinals(TerminalJournal.Entry entry,int count) throws Exception {
        var commands=jdbc.query("SELECT id,revision,destination,aggregate_key,payload FROM durable_outbox WHERE kind=? AND aggregate_key=? ORDER BY revision",
                (rs,n)->new com.chanter.common.events.DurableEvent(rs.getObject(1,UUID.class),1,rs.getString(3).substring("lifecycle-".length()),
                        rs.getLong(2),ErasedContent.FINAL,rs.getString(4),rs.getString(5)),
                ErasedContent.FINAL,"ACCOUNT_CONTENT_FINAL:"+entry.eventId()+":agent");
        assertThat(commands).hasSize(2);
        for(var command:commands) {
            var completion=mapper.readValue(command.payload(),ErasedContent.Completion.class);assertThat(completion.contentCount()).isEqualTo(count);
            var receipt=new com.chanter.common.events.DurableEvent(UUID.randomUUID(),1,command.producer(),command.revision(),ErasedContent.COMPLETE,
                    command.aggregateKey(),mapper.writeValueAsString(new ErasedContent.FinalReceipt(command.id(),completion)));
            content.accept(receipt);content.accept(receipt);
        }
    }

    @Test void hostedPendingNativeFixtureUsesCurrentChannelScopeAndOwningStoresWithoutCredentials() throws Exception {
        var source=java.nio.file.Path.of("../../scripts/deploy/fixtures/CanonicalLifecycleFixture.java").toAbsolutePath().normalize();
        var output=java.nio.file.Path.of("target/canonical-agent-fixture-test").toAbsolutePath();java.nio.file.Files.createDirectories(output);
        var compiler=javax.tools.ToolProvider.getSystemJavaCompiler();
        try(var files=compiler.getStandardFileManager(null,null,null)) {
            assertThat(compiler.getTask(null,files,null,List.of("-classpath",System.getProperty("java.class.path"),"-d",output.toString()),null,
                    files.getJavaFileObjects(source)).call()).isTrue();
        }
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()},getClass().getClassLoader())) {
            var type=loader.loadClass("CanonicalLifecycleFixture");var constructor=type.getDeclaredConstructor(org.springframework.context.ConfigurableApplicationContext.class);
            constructor.setAccessible(true);var fixture=constructor.newInstance(context);
            var execute=type.getDeclaredMethod("execute",com.fasterxml.jackson.databind.JsonNode.class);execute.setAccessible(true);
            UUID owner=UUID.randomUUID(),server=UUID.randomUUID(),course=UUID.randomUUID(),channel=UUID.randomUUID();
            var request=mapper.valueToTree(Map.of("action","agent-native-seed","serverId",server,"channelId",channel,"ownerId",owner));
            assertThatThrownBy(() -> execute.invoke(fixture,request)).hasRootCauseInstanceOf(ResponseStatusException.class);
            channelAccess.grantInstructorView(channel,owner,course,server,"Fixture");
            grantCandidates.registerGrantCandidates(server,owner,new StudyAssistantGrantCandidatesClient.GrantCandidates(server,List.of(),List.of()));
            var result=mapper.valueToTree(execute.invoke(fixture,request));
            assertThat(result.get("outcome").asText()).isEqualTo("ISSUED");assertThat(result.get("syntheticPendingOnly").asBoolean()).isTrue();
            assertThat(result.toString()).doesNotContain("session","installation","Token","evidence","question");
            UUID id=UUID.fromString(result.get("requestId").asText());
            assertThat(jdbc.queryForObject("SELECT reserved_tokens FROM ai_generation_usage WHERE id=?",Long.class,id)).isEqualTo(80);
            assertThat(jdbc.queryForObject("SELECT provider FROM ai_generation_usage WHERE id=?",String.class,id)).isEqualTo("codex-native");
            assertThat(jdbc.queryForObject("SELECT measured FROM ai_generation_usage WHERE id=?",Boolean.class,id)).isFalse();
            assertThat(jdbc.queryForObject("SELECT evidence_json FROM native_companion_requests WHERE id=?",String.class,id)).contains("Synthetic pending fixture");
            long before=jdbc.queryForObject("SELECT COALESCE(MAX(revision),0) FROM durable_outbox",Long.class);
            UUID resource=UUID.randomUUID();
            var change=new com.chanter.common.events.ResourceChanged(resource,null,null,null,null,false,true);
            var event=new com.chanter.common.events.DurableEvent(UUID.randomUUID(),1,"media",1,"RESOURCE_CHANGED","RESOURCE:"+resource,mapper.writeValueAsString(change));
            var delivery=mapper.valueToTree(Map.of("action","deliver","event",event));
            assertThat(mapper.valueToTree(execute.invoke(fixture,delivery)).get("committed").asBoolean()).isTrue();
            execute.invoke(fixture,delivery);
            var emitted=mapper.valueToTree(execute.invoke(fixture,mapper.valueToTree(Map.of("action","events","afterRevision",before))));
            assertThat(emitted.size()).isEqualTo(1);
            assertThat(emitted.get(0).get("destination").asText()).isEqualTo("media");
            var receipt=mapper.treeToValue(emitted.get(0).get("event"),com.chanter.common.events.DurableEvent.class);
            assertThat(mapper.readValue(receipt.payload(),com.chanter.common.events.ResourceDeletionReceipt.class))
                    .isEqualTo(new com.chanter.common.events.ResourceDeletionReceipt(resource,event.id()));
            var wrong=new com.chanter.common.events.DurableEvent(UUID.randomUUID(),1,"community",2,event.kind(),event.aggregateKey(),event.payload());
            assertThatThrownBy(() -> execute.invoke(fixture,mapper.valueToTree(Map.of("action","deliver","event",wrong))))
                    .hasRootCauseInstanceOf(ResponseStatusException.class);
        }
    }

    @Test void accountRetainsExactAnswerNotificationIdentityUntilItsDownstreamContentReceipts() throws Exception {
        UUID server=UUID.randomUUID(),user=UUID.randomUUID();install(server,user);
        var answer=new StudyAssistantAnswer(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),server,user,"private question","private answer",AnswerConfidence.HIGH,false,List.of(),Instant.now());
        answers.saveAnswer(answer,InvocationType.GROUNDED_ANSWER);
        var entry=entry("ACCOUNT",user);apply(entry);apply(entry);
        assertThat(answers.findBySupportQuestionId(answer.supportQuestionId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_erased_content WHERE target_kind='ACCOUNT' AND target_id=? AND source_kind='STUDY_ASSISTANT_ANSWER' AND source_id=?",
                Integer.class,user,answer.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind='ACCOUNT_CONTENT_ADVANCE' AND aggregate_key=?",Integer.class,"ACCOUNT_CONTENT_ADVANCE:"+entry.eventId())).isEqualTo(1);
        TerminalReapplyStore.Cleanup state=new TransactionTemplate(transactions).execute(s -> terminal.cleanup(entry));
        assertThat(state).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
        var advance=jdbc.queryForObject("SELECT id,revision,payload FROM durable_outbox WHERE kind='ACCOUNT_CONTENT_ADVANCE' AND aggregate_key=?",
                (rs,n)->new com.chanter.common.events.DurableEvent(rs.getObject(1,UUID.class),1,"agent",rs.getLong(2),ErasedContentDelivery.ADVANCE,"ACCOUNT_CONTENT_ADVANCE:"+entry.eventId(),rs.getString(3)),
                "ACCOUNT_CONTENT_ADVANCE:"+entry.eventId());content.accept(advance);
        var erasures=jdbc.query("SELECT id,revision,destination,aggregate_key,payload FROM durable_outbox WHERE kind=? AND aggregate_key LIKE ?",
                (rs,n)->new com.chanter.common.events.DurableEvent(rs.getObject(1,UUID.class),1,rs.getString(3).substring("lifecycle-".length()),rs.getLong(2),ErasedContent.ERASE,rs.getString(4),rs.getString(5)),
                ErasedContent.ERASE,"ACCOUNT_CONTENT:"+entry.eventId()+":%");assertThat(erasures).hasSize(2);
        for(var command:erasures) content.accept(new com.chanter.common.events.DurableEvent(UUID.randomUUID(),1,command.producer(),command.revision(),ErasedContent.RECEIPT,command.aggregateKey(),
                mapper.writeValueAsString(new ErasedContent.Receipt("agent",command.id(),mapper.readValue(command.payload(),ErasedContent.Batch.class)))));
        acknowledgeFinals(entry,1);
        var tx=new TransactionTemplate(transactions);
        assertThat(tx.<TerminalReapplyStore.Cleanup>execute(s -> terminal.cleanup(entry))).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
        tx.executeWithoutResult(s -> retractions.acknowledge(new com.chanter.common.events.AnswerReconciliation(
                new com.chanter.common.events.AnswerRetraction(answer.id(),answer.supportQuestionId(),answer.channelId(),user),"COMPLETE")));
        assertThat(tx.<TerminalReapplyStore.Cleanup>execute(s -> terminal.cleanup(entry))).isEqualTo(TerminalReapplyStore.Cleanup.PRESERVED);
    }

    @Test void lateSettlementWaitsForAttributionErasureAndCannotRestoreProviderMetadata() throws Exception {
        UUID server=UUID.randomUUID(),user=UUID.randomUUID();install(server,user);
        UUID reservation=ledger.reserve(server,UUID.randomUUID(),user,"fixture",model);var entry=entry("ACCOUNT",user);
        var erased=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var deletion=pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                terminal.applyTerminal(entry);erased.countDown();
                try {if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Fixture release missing");}
                catch(InterruptedException failure) {Thread.currentThread().interrupt();throw new IllegalStateException(failure);}
            }));
            try {
                assertThat(erased.await(5,TimeUnit.SECONDS)).isTrue();
                var settlement=pool.submit(() -> {attempting.countDown();ledger.settle(reservation,new LlmUsage(0,0,0,0,0),"CANCELLED",0,"fixture","late-request",model,false);});
                assertThat(attempting.await(5,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> settlement.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();deletion.get(5,TimeUnit.SECONDS);settlement.get(5,TimeUnit.SECONDS);
            } finally {release.countDown();}
        }
        assertThat(jdbc.queryForObject("SELECT provider_request_id FROM ai_generation_usage WHERE id=?",String.class,reservation)).isNull();
        assertThat(ledger.summary(server).accountedTokens()).isEqualTo(80);
        assertThat(ledger.summary(server).unknownUsageCount()).isEqualTo(1);
    }

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
        UUID acceptedEvent=jdbc.queryForObject("SELECT id FROM durable_outbox WHERE aggregate_key=? AND kind='ACCEPTED_ANSWER'",UUID.class,"ACCEPTED_ANSWER:"+question);
        var claimed=claimOutbox.claim().orElseThrow();
        for(int preceding=0;!claimed.event().id().equals(acceptedEvent) && preceding<64;preceding++)
            claimed=claimOutbox.claim().orElseThrow();
        assertThat(claimed.event().id()).isEqualTo(acceptedEvent);
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
        assertThat(cleanup).isEqualTo(TerminalReapplyStore.Cleanup.COMPLETE);
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
            assertThatThrownBy(() -> settling.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> late.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); deletion.get(5,TimeUnit.SECONDS);
            settling.get(5,TimeUnit.SECONDS);
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
