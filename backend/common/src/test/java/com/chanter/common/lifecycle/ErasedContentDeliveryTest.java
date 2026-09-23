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
            terminal=new TerminalReapplyStore(jdbc,tx,name,e -> TerminalReapplyStore.Cleanup.PENDING);
            var factory=new DefaultListableBeanFactory();factory.registerSingleton("terminal",terminal);
            factory.registerSingleton("erase",(ErasedContentReceiver.Erase)ref -> { });
            outbox=new DurableOutbox(jdbc,tx,name,Clock.systemUTC());
            delivery=new ErasedContentDelivery(name,jdbc,tx,outbox,mapper,factory.getBeanProvider(TerminalReapplyStore.class));
            receiver=new ErasedContentReceiver(name,jdbc,tx,outbox,mapper,factory.getBeanProvider(TerminalReapplyStore.class),factory.getBeanProvider(ErasedContentReceiver.Erase.class));
        }
        List<DurableEvent> events(String kind,String destination) {
            return jdbc.query("SELECT * FROM durable_outbox WHERE kind=?"+(destination==null ? "" : " AND destination=?")+" ORDER BY revision",
                    (rs,n)->new DurableEvent(rs.getObject("id",UUID.class),1,name,rs.getLong("revision"),rs.getString("kind"),rs.getString("aggregate_key"),rs.getString("payload")),
                    destination==null ? new Object[]{kind} : new Object[]{kind,destination});
        }
    }
}
