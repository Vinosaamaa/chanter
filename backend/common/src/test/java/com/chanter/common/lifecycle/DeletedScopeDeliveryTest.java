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

class DeletedScopeDeliveryTest {
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    @Test void onePagePerCommandAndFinalReceiptCommitWithVerifiedImportWhileReplayStaysIdempotent() throws Exception {
        var community=new Source("community"); var message=new Source("message");
        var entry=entry(); var ids=new ArrayList<UUID>(); for(int n=1;n<=257;n++) ids.add(new UUID(0,n));
        String digest=DeletedScope.startDigest(entry.digest(),"COURSE",ids.size());
        for(UUID id:ids) digest=DeletedScope.nextDigest(digest,id);
        final String expected=digest;
        community.beans.registerSingleton("pages",(DeletedScopeDelivery.Source)(e,kind,after) -> {
            if(kind.equals("CHANNEL")) return new DeletedScope.Page(1,e.targetId(),e.revision(),e.eventId(),e.digest(),kind,after,0,DeletedScope.startDigest(e.digest(),kind,0),List.of(),null);
            int start=after.equals(DeletedScope.START) ? 0 : 256;
            var page=ids.subList(start,Math.min(start+256,ids.size()));
            return new DeletedScope.Page(1,e.targetId(),e.revision(),e.eventId(),e.digest(),kind,after,ids.size(),expected,List.copyOf(page),start==0 ? page.getLast() : null);
        });
        community.tx.executeWithoutResult(s -> { community.terminal.applyTerminal(entry); community.delivery.start(entry); community.delivery.start(entry); });
        message.tx.executeWithoutResult(s -> message.terminal.applyTerminal(entry));
        assertThat(community.events(DeletedScopeDelivery.ADVANCE,"lifecycle-community")).hasSize(2);
        var first=community.events(DeletedScopeDelivery.ADVANCE,"lifecycle-community").stream().filter(e -> e.payload().contains("COURSE")).findFirst().orElseThrow();
        community.delivery.accept(first); community.delivery.accept(first);
        assertThat(community.events(DeletedScopeDelivery.IMPORT,null)).hasSize(5);
        var second=community.events(DeletedScopeDelivery.ADVANCE,"lifecycle-community").stream().filter(e -> e.revision()>first.revision() && e.payload().contains("COURSE")).findFirst().orElseThrow();
        community.delivery.accept(second);
        var pages=community.events(DeletedScopeDelivery.IMPORT,"lifecycle-message"); assertThat(pages).hasSize(2);
        assertThatThrownBy(() -> message.delivery.accept(pages.getLast())).hasMessageContaining("Missing scope prefix");
        message.delivery.accept(pages.getFirst());
        message.jdbc.execute("ALTER TABLE durable_outbox ADD CONSTRAINT fixture_ready_failure CHECK(kind<>'DELETED_SCOPE_READY')");
        try { assertThatThrownBy(() -> message.delivery.accept(pages.getLast())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
        finally { message.jdbc.execute("ALTER TABLE durable_outbox DROP CONSTRAINT fixture_ready_failure"); }
        assertThat(message.imports.ready(entry,"COURSE")).isFalse();
        assertThat(message.jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_scope_import_ids",Integer.class)).isEqualTo(256);
        message.delivery.accept(pages.getLast()); message.delivery.accept(pages.getLast());
        assertThat(message.imports.ready(entry,"COURSE")).isTrue();
        var ready=message.events(DeletedScopeDelivery.READY,"lifecycle-community"); assertThat(ready).hasSize(1);
        community.delivery.accept(ready.getFirst()); community.delivery.accept(ready.getFirst());
        assertThat(community.jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_scope_delivery",Integer.class)).isEqualTo(1);
        assertThat(community.jdbc.queryForObject("SELECT total_count FROM lifecycle_scope_delivery",Long.class)).isEqualTo(257);
        var emptyKind=community.events(DeletedScopeDelivery.ADVANCE,"lifecycle-community").stream().filter(e -> e.payload().contains("CHANNEL")).findFirst().orElseThrow();
        community.delivery.accept(emptyKind);
        var emptyPage=community.events(DeletedScopeDelivery.IMPORT,"lifecycle-message").stream().filter(e -> e.payload().contains("CHANNEL")).findFirst().orElseThrow();
        message.delivery.accept(emptyPage);
        var emptyReady=message.events(DeletedScopeDelivery.READY,"lifecycle-community").stream().filter(e -> e.payload().contains("CHANNEL")).findFirst().orElseThrow();
        community.delivery.accept(emptyReady);
        assertThat(community.delivery.complete(entry)).isFalse();
        for(String destination:DeletedScopeDelivery.DESTINATIONS) {
            if(destination.equals("message")) continue;
            var recipient=new Source(destination);recipient.tx.executeWithoutResult(s -> recipient.terminal.applyTerminal(entry));
            for(var page:community.events(DeletedScopeDelivery.IMPORT,"lifecycle-"+destination)) recipient.delivery.accept(page);
            for(var receipt:recipient.events(DeletedScopeDelivery.READY,"lifecycle-community")) community.delivery.accept(receipt);
        }
        assertThat(community.delivery.complete(entry)).isTrue();
        assertThat(community.jdbc.queryForObject("SELECT SUM(total_count) FROM lifecycle_scope_delivery WHERE scope_kind='CHANNEL'",Long.class)).isZero();
        var spoof=new DurableEvent(UUID.randomUUID(),1,"media",100,first.kind(),first.aggregateKey(),first.payload());
        assertThatThrownBy(() -> community.delivery.accept(spoof)).hasMessageContaining("Invalid scope delivery");
    }
    private TerminalJournal.Entry entry() {
        UUID event=UUID.randomUUID(),server=UUID.randomUUID(); Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        return new TerminalJournal.Entry(1,event,"STUDY_SERVER",server,"DELETE",now,TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,
                TerminalJournal.digest(1,event,"STUDY_SERVER",server,now,TerminalJournal.GENESIS));
    }
    private final class Source {
        final JdbcTemplate jdbc; final TransactionTemplate tx; final DefaultListableBeanFactory beans=new DefaultListableBeanFactory();
        final TerminalReapplyStore terminal; final DeletedScopeStore imports; final DeletedScopeDelivery delivery; final String name;
        Source(String name) {
            this.name=name; var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
            jdbc=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
            jdbc.execute(DurableOutbox.SCHEMA); jdbc.execute(DurableConsumer.SCHEMA); jdbc.execute(TerminalReapplyStore.SCHEMA);
            jdbc.execute(DeletedScopeStore.SCHEMA); jdbc.execute(DeletedScopeDelivery.SCHEMA);
            terminal=new TerminalReapplyStore(jdbc,tx,name,e -> TerminalReapplyStore.Cleanup.PENDING);
            imports=new DeletedScopeStore(jdbc,tx,terminal::cleanup,terminal::reconcile);
            beans.registerSingleton("terminal",terminal); beans.registerSingleton("imports",imports);
            delivery=new DeletedScopeDelivery(name,jdbc,tx,new DurableOutbox(jdbc,tx,name,Clock.systemUTC()),mapper,
                    beans.getBeanProvider(DeletedScopeDelivery.Source.class),beans.getBeanProvider(DeletedScopeStore.class),beans.getBeanProvider(TerminalReapplyStore.class),false);
        }
        List<DurableEvent> events(String kind,String destination) {
            return jdbc.query("SELECT id,revision,kind,aggregate_key,payload FROM durable_outbox WHERE kind=?"+(destination==null ? "" : " AND destination=?")+" ORDER BY revision",
                    (rs,n) -> new DurableEvent(rs.getObject(1,UUID.class),1,name,rs.getLong(2),rs.getString(3),rs.getString(4),rs.getString(5)),
                    destination==null ? new Object[]{kind} : new Object[]{kind,destination});
        }
    }
}
