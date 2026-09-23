package com.chanter.common.lifecycle;

import static org.assertj.core.api.Assertions.*;
import com.chanter.common.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

class ErasedContentDeliveryTest {
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    @Test void recipientTerminalErasesPendingAndClaimedPayloadsAndRejectsLaterSourceCopiesAtomically() {
        var source=new Node("message");var entry=entry();UUID question=UUID.randomUUID(),other=UUID.randomUUID();
        var writer=new NotificationEventWriter(source.outbox,mapper,source.jdbc);
        source.tx.executeWithoutResult(s -> {
            writer.append(questionUpdate(entry.targetId(),question));
            writer.append(questionUpdate(entry.targetId(),UUID.randomUUID()));
            writer.append(questionUpdate(other,question));
        });
        var claimed=source.outbox.claim().orElseThrow();
        source.tx.executeWithoutResult(s -> {
            source.terminal.applyTerminal(entry);source.delivery.start(entry);s.setRollbackOnly();
        });
        assertThat(source.jdbc.queryForObject("SELECT status FROM durable_outbox WHERE id=?",String.class,claimed.event().id())).isEqualTo("SENDING");
        assertThat(source.jdbc.queryForObject("SELECT payload FROM durable_outbox WHERE id=?",String.class,claimed.event().id())).isNotEqualTo("{}");
        source.tx.executeWithoutResult(s -> {source.terminal.applyTerminal(entry);source.delivery.start(entry);});
        var advance=source.events(ErasedContentDelivery.ADVANCE,null).getFirst();
        source.tx.executeWithoutResult(s -> {source.delivery.accept(advance);s.setRollbackOnly();});
        assertThat(source.jdbc.queryForObject("SELECT payload FROM durable_outbox WHERE id=?",String.class,claimed.event().id())).isNotEqualTo("{}");
        source.delivery.accept(advance);
        source.outbox.failed(claimed,"LATE_FAILURE");source.outbox.delivered(claimed);
        source.tx.executeWithoutResult(s -> writer.append(questionUpdate(entry.targetId(),UUID.randomUUID())));
        assertThat(source.events("NOTIFICATION",null)).hasSize(3);
        assertThat(source.jdbc.queryForList("SELECT payload FROM durable_outbox WHERE aggregate_key LIKE ?",String.class,
                "NOTIFICATION:"+entry.targetId()+":%")).containsExactly("{}","{}");
        assertThat(source.jdbc.queryForList("SELECT status FROM durable_outbox WHERE aggregate_key LIKE ?",String.class,
                "NOTIFICATION:"+entry.targetId()+":%")).containsExactly("ERASED","ERASED");
        assertThat(source.jdbc.queryForObject("SELECT payload FROM durable_outbox WHERE aggregate_key LIKE ?",String.class,
                "NOTIFICATION:"+other+":%")).contains("Question update");
    }
    @Test void recipientPayloadRetirementUsesBoundedExistingAdvanceBeforeZeroContentFinal() {
        var source=new Node("community");var entry=entry();
        source.tx.executeWithoutResult(s -> {
            for(int n=0;n<257;n++) source.outbox.append("notification","NOTIFICATION","NOTIFICATION:"+entry.targetId()+":ANNOUNCEMENT:"+UUID.randomUUID()+":CREATED","{\"body\":\"private\"}");
            source.terminal.applyTerminal(entry);source.delivery.start(entry);
        });
        assertThat(source.events(ErasedContent.FINAL,null)).isEmpty();
        source.delivery.accept(source.events(ErasedContentDelivery.ADVANCE,null).getFirst());
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE status='ERASED'",Integer.class)).isEqualTo(256);
        assertThat(source.events(ErasedContent.FINAL,null)).isEmpty();
        source.delivery.accept(source.events(ErasedContentDelivery.ADVANCE,null).getLast());
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE status='ERASED'",Integer.class)).isEqualTo(257);
        assertThat(source.events(ErasedContent.FINAL,null)).hasSize(2);
    }
    @Test void explicitFinalsBindAllContentProducersAndBothRecipientsIncludingEmptySources() throws Exception {
        var community=new Node("community");var message=new Node("message");var search=new Node("search");var notification=new Node("notification");
        var media=new Node("media");var agent=new Node("agent");
        var entry=entry();
        for(var node:List.of(community,message,media,agent,search,notification)) node.tx.executeWithoutResult(s -> node.terminal.applyTerminal(entry));
        for(var node:List.of(media,agent)) node.tx.executeWithoutResult(s -> node.delivery.start(entry));
        community.tx.executeWithoutResult(s -> {
            community.jdbc.update("INSERT INTO lifecycle_erased_content(target_kind,target_id,revision,event_id,terminal_digest,source_kind,source_id) VALUES ('ACCOUNT',?,?,?,?,?,?)",
                    entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),"ANNOUNCEMENT",UUID.randomUUID());
            community.delivery.start(entry);
        });
        message.tx.executeWithoutResult(s -> message.delivery.start(entry));
        assertThat(community.events(ErasedContent.FINAL,null)).isEmpty();
        assertThat(message.events(ErasedContent.FINAL,null)).hasSize(2);
        assertThat(mapper.readValue(message.events(ErasedContent.FINAL,null).getFirst().payload(),ErasedContent.Completion.class).contentCount()).isZero();
        community.delivery.accept(community.events(ErasedContentDelivery.ADVANCE,null).getFirst());
        for(var recipient:List.of(search,notification)) {
            var erase=community.events(ErasedContent.ERASE,"lifecycle-"+recipient.name).getFirst();
            // A forged early final cannot replace the missing batch effects.
            var premature=new ErasedContent.Completion(entry,"community",1,1);
            assertThatThrownBy(() -> recipient.receiver.accept(new DurableEvent(UUID.randomUUID(),1,"community",erase.revision()+100,
                    ErasedContent.FINAL,premature.key(),mapper.writeValueAsString(premature)))).isInstanceOf(IllegalArgumentException.class);
            recipient.receiver.accept(erase);recipient.receiver.accept(erase);
            community.delivery.accept(recipient.events(ErasedContent.RECEIPT,null).getFirst());
        }
        assertThat(community.events(ErasedContent.FINAL,null)).hasSize(2);
        for(var recipient:List.of(search,notification)) {
            var finalEvent=community.events(ErasedContent.FINAL,"lifecycle-"+recipient.name).getFirst();
            recipient.receiver.accept(finalEvent);recipient.receiver.accept(finalEvent);
            assertThat(recipient.receiver.complete(entry)).isFalse();
            community.delivery.accept(recipient.events(ErasedContent.COMPLETE,"lifecycle-community").getFirst());
            var empty=message.events(ErasedContent.FINAL,"lifecycle-"+recipient.name).getFirst();
            recipient.jdbc.execute("ALTER TABLE durable_outbox ADD CONSTRAINT reject_final CHECK(kind<>'ACCOUNT_CONTENT_COMPLETE' OR destination<>'lifecycle-message')");
            try { assertThatThrownBy(() -> recipient.receiver.accept(empty)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
            finally { recipient.jdbc.execute("ALTER TABLE durable_outbox DROP CONSTRAINT reject_final"); }
            assertThat(recipient.receiver.complete(entry)).isFalse();
            recipient.receiver.accept(empty);recipient.receiver.accept(empty);
            assertThat(recipient.receiver.complete(entry)).isFalse();
            var ack=recipient.events(ErasedContent.COMPLETE,"lifecycle-message").getFirst();
            var receipt=mapper.readValue(ack.payload(),ErasedContent.FinalReceipt.class);
            var forged=new DurableEvent(UUID.randomUUID(),1,recipient.name,ack.revision()+100,ack.kind(),ack.aggregateKey(),
                    mapper.writeValueAsString(new ErasedContent.FinalReceipt(UUID.randomUUID(),receipt.completion())));
            assertThatThrownBy(() -> message.delivery.accept(forged)).isInstanceOf(IllegalArgumentException.class);
            message.delivery.accept(ack);message.delivery.accept(ack);
            for(var producer:List.of(media,agent)) {
                assertThat(recipient.receiver.complete(entry)).isFalse();
                recipient.receiver.accept(producer.events(ErasedContent.FINAL,"lifecycle-"+recipient.name).getFirst());
                producer.delivery.accept(recipient.events(ErasedContent.COMPLETE,"lifecycle-"+producer.name).getFirst());
            }
            assertThat(recipient.receiver.complete(entry)).isTrue();
            TerminalReapplyStore.Cleanup state=recipient.tx.execute(s -> recipient.terminal.cleanup(entry));
            assertThat(state).isEqualTo(TerminalReapplyStore.Cleanup.COMPLETE);
        }
        assertThat(community.delivery.complete(entry)).isTrue();assertThat(message.delivery.complete(entry)).isTrue();
        assertThat(media.delivery.complete(entry)).isTrue();assertThat(agent.delivery.complete(entry)).isTrue();
        var another=entry();
        assertThat(search.receiver.complete(another)).isFalse();
        assertThat(message.delivery.complete(another)).isFalse();
    }
    @Test void boundedSourceBatchesPurgeClaimedPayloadsAndBindBothReceiptsToExactOriginalCommands() throws Exception {
        var source=new Node("community");var search=new Node("search");var notification=new Node("notification");
        var entry=entry();
        for(var node:List.of(source,search,notification)) node.tx.executeWithoutResult(s -> node.terminal.applyTerminal(entry));
        UUID first=new UUID(0,1);
        source.tx.executeWithoutResult(s -> {
            for(int n=1;n<=257;n++) source.jdbc.update("INSERT INTO lifecycle_erased_content(target_kind,target_id,revision,event_id,terminal_digest,source_kind,source_id) VALUES ('ACCOUNT',?,?,?,?,?,?)",
                    entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),"ANNOUNCEMENT",new UUID(0,n));
            source.outbox.append("notification","NOTIFICATION","NOTIFICATION:"+UUID.randomUUID()+":ANNOUNCEMENT:"+first+":SUPPORT_QUESTION_CREATED","{\"body\":\"private preview\"}");
            source.delivery.start(entry);source.delivery.start(entry);
        });
        var claimed=source.outbox.claim().orElseThrow();
        assertThat(claimed.event().payload()).contains("private preview");
        var initial=source.events(ErasedContentDelivery.ADVANCE,null);assertThat(initial).hasSize(1);
        source.delivery.accept(initial.getFirst());source.delivery.accept(initial.getFirst());
        assertThat(source.events(ErasedContent.ERASE,null)).hasSize(2);
        assertThat(source.jdbc.queryForObject("SELECT status FROM durable_outbox WHERE id=?",String.class,claimed.event().id())).isEqualTo("ERASED");
        source.outbox.failed(claimed,"LATE_FAILURE");source.outbox.delivered(claimed);
        assertThat(source.jdbc.queryForObject("SELECT payload FROM durable_outbox WHERE id=?",String.class,claimed.event().id())).isEqualTo("{}");
        assertThat(source.outbox.replay(claimed.event().id())).isFalse();
        var second=source.events(ErasedContentDelivery.ADVANCE,null).getLast();source.delivery.accept(second);
        assertThat(source.events(ErasedContent.ERASE,null)).hasSize(4);
        for(var destination:List.of(search,notification)) {
            var events=source.events(ErasedContent.ERASE,"lifecycle-"+destination.name);
            // Independent exact-content batches may arrive in any order.
            destination.receiver.accept(events.getLast());destination.receiver.accept(events.getFirst());
            for(var receipt:destination.events(ErasedContent.RECEIPT,null)) { source.delivery.accept(receipt);source.delivery.accept(receipt); }
        }
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_erased_content WHERE search_ack=TRUE AND notification_ack=TRUE",Integer.class)).isEqualTo(257);
        assertThat(search.jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_erased_content_fences",Integer.class)).isEqualTo(257);
        assertThat(source.jdbc.queryForObject("SELECT cleanup_state FROM lifecycle_terminal_targets WHERE target_id=?",String.class,entry.targetId())).isEqualTo("PENDING");
        var receipt=notification.events(ErasedContent.RECEIPT,null).getFirst();
        var actual=mapper.readValue(receipt.payload(),ErasedContent.Receipt.class);
        var forged=new ErasedContent.Receipt(actual.owner(),UUID.randomUUID(),actual.batch());
        var wrong=new DurableEvent(UUID.randomUUID(),1,"notification",1000,receipt.kind(),receipt.aggregateKey(),mapper.writeValueAsString(forged));
        assertThatThrownBy(() -> source.delivery.accept(wrong)).hasMessageContaining("Invalid erased content");
    }
    @Test void dispositionFailureRollsBackTheSourceBatchAndRetainedPayloadErasure() {
        var source=new Node("community");var entry=entry();
        UUID content=UUID.randomUUID();
        source.tx.executeWithoutResult(s -> {
            source.terminal.applyTerminal(entry);
            source.jdbc.update("INSERT INTO lifecycle_erased_content(target_kind,target_id,revision,event_id,terminal_digest,source_kind,source_id) VALUES ('ACCOUNT',?,?,?,?,?,?)",
                    entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),"ANNOUNCEMENT",content);
            source.outbox.append("search","ANNOUNCEMENT","ANNOUNCEMENT:"+content,"{\"body\":\"retained fixture\"}");
            source.delivery.start(entry);
        });
        var advance=source.events(ErasedContentDelivery.ADVANCE,null).getFirst();
        source.jdbc.execute("ALTER TABLE lifecycle_erased_content ADD CONSTRAINT batch_failure CHECK(search_event_id IS NULL)");
        try { assertThatThrownBy(() -> source.delivery.accept(advance)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
        finally { source.jdbc.execute("ALTER TABLE lifecycle_erased_content DROP CONSTRAINT batch_failure"); }
        assertThat(source.events(ErasedContent.ERASE,null)).isEmpty();
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_erased_content WHERE search_event_id IS NOT NULL",Integer.class)).isZero();
        assertThat(source.jdbc.queryForObject("SELECT payload FROM durable_outbox WHERE kind='ANNOUNCEMENT'",String.class)).contains("retained fixture");
        assertThat(source.jdbc.queryForObject("SELECT status FROM durable_outbox WHERE kind='ANNOUNCEMENT'",String.class)).isEqualTo("PENDING");
        source.delivery.accept(advance);assertThat(source.events(ErasedContent.ERASE,null)).hasSize(2);
    }
    @Test void sharedQuestionPreviewSanitationIsBoundedAndPreservesPendingLaterHumanDelivery() throws Exception {
        var source=new Node("message");var entry=entry();UUID question=UUID.randomUUID();
        source.tx.executeWithoutResult(s -> {
            source.terminal.applyTerminal(entry);
            source.jdbc.update("INSERT INTO lifecycle_erased_content(target_kind,target_id,revision,event_id,terminal_digest,source_kind,source_id) VALUES ('ACCOUNT',?,?,?,?,?,?)",
                    entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),"QUESTION_PREVIEW",question);
            for(int n=0;n<65;n++) {
                UUID user=UUID.randomUUID();var payload=questionUpdate(user,question);
                try { source.outbox.append("notification","NOTIFICATION",NotificationEventWriter.aggregateKey(user,"SUPPORT_QUESTION",question,"SUPPORT_QUESTION_ANSWERED"),mapper.writeValueAsString(payload)); }
                catch(Exception failure) { throw new IllegalStateException(failure); }
            }
            source.delivery.start(entry);
        });
        var claimed=source.outbox.claim().orElseThrow();
        assertThat(claimed.event().payload()).contains("private body");
        var later=questionUpdate(UUID.randomUUID(),question);
        source.tx.executeWithoutResult(s -> new NotificationEventWriter(source.outbox,mapper,source.jdbc).append(later));
        var lateEvent=source.events("NOTIFICATION",null).getLast();
        assertThat(later.get("bodyPreview")).isEqualTo("private body");
        source.delivery.accept(source.events(ErasedContentDelivery.ADVANCE,null).getFirst());
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_content_redactions",Integer.class)).isEqualTo(64);
        assertThat(source.events(ErasedContent.ERASE,null)).isEmpty();
        source.delivery.accept(source.events(ErasedContentDelivery.ADVANCE,null).getLast());
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_content_redactions",Integer.class)).isEqualTo(65);
        assertThat(source.events(ErasedContent.ERASE,null)).hasSize(2);
        assertThat(source.events("NOTIFICATION",null)).allSatisfy(e -> assertThat(e.payload()).contains("Question update").doesNotContain("private body","private label"));
        assertThat(source.jdbc.queryForObject("SELECT status FROM durable_outbox WHERE id=?",String.class,lateEvent.id())).isEqualTo("PENDING");
        assertThat(source.jdbc.queryForObject("SELECT status FROM durable_outbox WHERE id=?",String.class,claimed.event().id())).isEqualTo("SENDING");
        source.outbox.failed(claimed,"TRANSIENT_DELIVERY_FAILURE");
        assertThat(source.jdbc.queryForObject("SELECT payload FROM durable_outbox WHERE id=?",String.class,claimed.event().id())).doesNotContain("private body");
    }
    private Map<String,Object> questionUpdate(UUID recipient,UUID question) {
        return Map.of("userId",recipient,"kind","SUPPORT_QUESTION_ANSWERED","sourceType","SUPPORT_QUESTION","sourceId",question,
                "title","Answered","bodyPreview","private body","courseLabel","private label","href","/app/inbox");
    }
    private TerminalJournal.Entry entry() {
        UUID event=UUID.randomUUID(),account=UUID.randomUUID();Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        return new TerminalJournal.Entry(1,event,"ACCOUNT",account,"DELETE",now,TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,
                TerminalJournal.digest(1,event,"ACCOUNT",account,now,TerminalJournal.GENESIS));
    }
    private final class Node {
        final String name;final JdbcTemplate jdbc;final TransactionTemplate tx;final DurableOutbox outbox;
        final TerminalReapplyStore terminal;final ErasedContentDelivery delivery;final ErasedContentReceiver receiver;
        Node(String name) {
            this.name=name;var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
            jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
            jdbc.execute(DurableOutbox.SCHEMA);jdbc.execute(DurableConsumer.SCHEMA);jdbc.execute(TerminalReapplyStore.SCHEMA);jdbc.execute(ErasedContentReceiver.SCHEMA);
            jdbc.execute("CREATE TABLE lifecycle_erased_content(target_kind VARCHAR(16),target_id UUID,revision BIGINT,event_id UUID,terminal_digest VARCHAR(64),source_kind VARCHAR(24),source_id UUID,PRIMARY KEY(target_kind,target_id,source_kind,source_id))");
            jdbc.execute(ErasedContentDelivery.SCHEMA);
            terminal=new TerminalReapplyStore(jdbc,tx,name,this::cleanup);
            var factory=new DefaultListableBeanFactory();factory.registerSingleton("terminal",terminal);
            factory.registerSingleton("erase",(ErasedContentReceiver.Erase)ref -> { });
            outbox=new DurableOutbox(jdbc,tx,name,Clock.systemUTC());
            delivery=new ErasedContentDelivery(name,jdbc,tx,outbox,mapper,factory.getBeanProvider(TerminalReapplyStore.class));
            receiver=new ErasedContentReceiver(name,jdbc,tx,outbox,mapper,factory.getBeanProvider(TerminalReapplyStore.class),factory.getBeanProvider(ErasedContentReceiver.Erase.class));
        }
        private TerminalReapplyStore.Cleanup cleanup(TerminalJournal.Entry entry) {
            return Set.of("search","notification").contains(name) && receiver.complete(entry)
                    ? TerminalReapplyStore.Cleanup.COMPLETE : TerminalReapplyStore.Cleanup.PENDING;
        }
        List<DurableEvent> events(String kind,String destination) {
            return jdbc.query("SELECT * FROM durable_outbox WHERE kind=?"+(destination==null ? "" : " AND destination=?")+" ORDER BY revision",
                    (rs,n)->new DurableEvent(rs.getObject("id",UUID.class),1,name,rs.getLong("revision"),rs.getString("kind"),rs.getString("aggregate_key"),rs.getString("payload")),
                    destination==null ? new Object[]{kind} : new Object[]{kind,destination});
        }
    }
}
