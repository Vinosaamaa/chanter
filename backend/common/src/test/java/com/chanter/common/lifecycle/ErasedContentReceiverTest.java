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

class ErasedContentReceiverTest {
    @Test void authorityFencePayloadAndReceiptAreAtomicAndDuplicateDeliveryIsIdempotent() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds); var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute(TerminalReapplyStore.SCHEMA);jdbc.execute(DurableConsumer.SCHEMA);jdbc.execute(DurableOutbox.SCHEMA);
        jdbc.execute(ErasedContentReceiver.SCHEMA);jdbc.execute("CREATE TABLE fixture_content(id UUID PRIMARY KEY,body TEXT)");
        var terminal=new TerminalReapplyStore(jdbc,tx,"search",entry -> TerminalReapplyStore.Cleanup.PENDING);
        var factory=new DefaultListableBeanFactory();factory.registerSingleton("terminal",terminal);
        factory.registerSingleton("erase",(ErasedContentReceiver.Erase)ref -> jdbc.update("DELETE FROM fixture_content WHERE id=?",ref.id()));
        var mapper=new ObjectMapper().findAndRegisterModules();
        var receiver=new ErasedContentReceiver("search",jdbc,tx,new DurableOutbox(jdbc,tx,"search",Clock.systemUTC()),mapper,
                factory.getBeanProvider(TerminalReapplyStore.class),factory.getBeanProvider(ErasedContentReceiver.Erase.class));
        var entry=entry();UUID content=UUID.randomUUID();jdbc.update("INSERT INTO fixture_content VALUES (?,?)",content,"private preview");
        var batch=new ErasedContent.Batch(entry,List.of(new ErasedContent.Ref("ANNOUNCEMENT",content)));
        var event=new DurableEvent(UUID.randomUUID(),1,"community",10,ErasedContent.ERASE,batch.key(UUID.randomUUID()),mapper.writeValueAsString(batch));
        assertThatThrownBy(() -> receiver.accept(event)).hasMessageContaining("Unknown terminal authority");
        tx.executeWithoutResult(s -> terminal.applyTerminal(entry));
        jdbc.execute("ALTER TABLE durable_outbox ADD CONSTRAINT receipt_failure CHECK(kind<>'ACCOUNT_CONTENT_ERASED')");
        try { assertThatThrownBy(() -> receiver.accept(event)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
        finally { jdbc.execute("ALTER TABLE durable_outbox DROP CONSTRAINT receipt_failure"); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fixture_content",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_erased_content_fences",Integer.class)).isZero();
        receiver.accept(event);receiver.accept(event);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fixture_content",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_erased_content_fences",Integer.class)).isEqualTo(1);
        var payloads=jdbc.query("SELECT payload FROM durable_outbox",(rs,n)->rs.getString(1));assertThat(payloads).hasSize(1);
        var receipt=mapper.readValue(payloads.getFirst(),ErasedContent.Receipt.class);receipt.validate();
        assertThat(receipt.commandId()).isEqualTo(event.id());assertThat(receipt.batch()).isEqualTo(batch);
        assertThat(payloads.getFirst()).doesNotContain("private preview");
        var wrong=new DurableEvent(UUID.randomUUID(),1,"message",11,event.kind(),event.aggregateKey(),event.payload());
        assertThatThrownBy(() -> receiver.accept(wrong)).hasMessageContaining("Invalid erased content");
    }
    @Test void boundKindsAndCanonicalTextOrderingRejectDuplicatesAndOversizedCommands() {
        var entry=entry();
        var low=new ErasedContent.Ref("MESSAGE",UUID.fromString("7fffffff-ffff-4fff-8fff-ffffffffffff"));
        var high=new ErasedContent.Ref("MESSAGE",UUID.fromString("80000000-0000-4000-8000-000000000000"));
        new ErasedContent.Batch(entry,List.of(low,high)).validate("message");
        assertThatThrownBy(() -> new ErasedContent.Batch(entry,List.of(high,low)).validate("message")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ErasedContent.Batch(entry,List.of(low,low)).validate("message")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ErasedContent.Batch(entry,List.of(low)).validate("community")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ErasedContent.Batch(entry,Collections.nCopies(257,low)).validate("message")).isInstanceOf(IllegalArgumentException.class);
    }
    private TerminalJournal.Entry entry() {
        UUID event=UUID.randomUUID(),account=UUID.randomUUID();Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        return new TerminalJournal.Entry(1,event,"ACCOUNT",account,"DELETE",now,TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,
                TerminalJournal.digest(1,event,"ACCOUNT",account,now,TerminalJournal.GENESIS));
    }
}
