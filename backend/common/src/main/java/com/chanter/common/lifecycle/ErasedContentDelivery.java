package com.chanter.common.lifecycle;

import com.chanter.common.events.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded source work on the existing outbox; per-content fields are erasure dispositions, not a retry queue. */
public final class ErasedContentDelivery {
    public static final String ADVANCE="ACCOUNT_CONTENT_ADVANCE";
    public static final String SCHEMA="""
        ALTER TABLE lifecycle_erased_content ADD COLUMN search_event_id UUID;
        ALTER TABLE lifecycle_erased_content ADD COLUMN notification_event_id UUID;
        ALTER TABLE lifecycle_erased_content ADD COLUMN search_ack BOOLEAN NOT NULL DEFAULT FALSE;
        ALTER TABLE lifecycle_erased_content ADD COLUMN notification_ack BOOLEAN NOT NULL DEFAULT FALSE;
        CREATE TABLE lifecycle_content_dispatch (account_id UUID PRIMARY KEY,advance_event_id UUID);
        CREATE INDEX lifecycle_content_search_event ON lifecycle_erased_content(search_event_id);
        CREATE INDEX lifecycle_content_notification_event ON lifecycle_erased_content(notification_event_id);
        CREATE INDEX lifecycle_content_pending ON lifecycle_erased_content(target_kind,target_id,search_event_id,source_kind,source_id)
        """;
    private final String source;
    private final JdbcTemplate jdbc;
    private final DurableConsumer consumer;
    private final DurableOutbox outbox;
    private final ObjectMapper mapper;
    private final ObjectProvider<TerminalReapplyStore> terminal;
    public ErasedContentDelivery(String source,JdbcTemplate jdbc,TransactionTemplate tx,DurableOutbox outbox,ObjectMapper mapper,
            ObjectProvider<TerminalReapplyStore> terminal) {
        this.source=source;this.jdbc=jdbc;this.consumer=new DurableConsumer(jdbc,tx);this.outbox=outbox;this.mapper=mapper;this.terminal=terminal;
    }
    public static boolean command(String kind) { return ADVANCE.equals(kind) || ErasedContent.RECEIPT.equals(kind); }
    /** Called after owning erasure, with terminal authority already locked in its source transaction. */
    public void start(TerminalJournal.Entry entry) {
        requireSource();entry.validate();
        if(!TransactionSynchronizationManager.isActualTransactionActive() || !entry.targetKind().equals("ACCOUNT")) throw invalid();
        if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_content_dispatch WHERE account_id=?",Integer.class,entry.targetId())==0)
            jdbc.update("INSERT INTO lifecycle_content_dispatch(account_id) VALUES (?)",entry.targetId());
        UUID pending=jdbc.queryForObject("SELECT advance_event_id FROM lifecycle_content_dispatch WHERE account_id=?",UUID.class,entry.targetId());
        if(pending==null && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM lifecycle_erased_content WHERE target_kind='ACCOUNT' AND target_id=? AND search_event_id IS NULL)",Boolean.class,entry.targetId()))) {
            UUID event=append(source,ADVANCE,advanceKey(entry),entry);
            jdbc.update("UPDATE lifecycle_content_dispatch SET advance_event_id=? WHERE account_id=?",event,entry.targetId());
        }
    }
    public void accept(DurableEvent event) {
        requireSource();event.validate();
        if(ADVANCE.equals(event.kind())) {
            var entry=read(event,TerminalJournal.Entry.class);entry.validate();
            if(!entry.targetKind().equals("ACCOUNT") || !source.equals(event.producer()) || !advanceKey(entry).equals(event.aggregateKey())) throw invalid();
            consumer.apply(event,false,() -> {
                lock(entry);
                UUID expected=jdbc.queryForObject("SELECT advance_event_id FROM lifecycle_content_dispatch WHERE account_id=?",UUID.class,entry.targetId());
                if(!event.id().equals(expected)) throw invalid();
                var refs=jdbc.query("""
                    SELECT source_kind,source_id FROM lifecycle_erased_content
                    WHERE target_kind='ACCOUNT' AND target_id=? AND search_event_id IS NULL
                    ORDER BY source_kind,source_id LIMIT 256
                    """,(rs,n)->new ErasedContent.Ref(rs.getString(1),rs.getObject(2,UUID.class)),entry.targetId());
                if(!refs.isEmpty()) {
                    var batch=new ErasedContent.Batch(entry,refs);batch.validate(source);
                    String key=batch.key(UUID.randomUUID());
                    UUID search=append("search",ErasedContent.ERASE,key,batch),notification=append("notification",ErasedContent.ERASE,key,batch);
                    for(var ref:refs) {
                        purge(ref);
                        jdbc.update("""
                            UPDATE lifecycle_erased_content SET search_event_id=?,notification_event_id=?
                            WHERE target_kind='ACCOUNT' AND target_id=? AND source_kind=? AND source_id=? AND search_event_id IS NULL
                            """,search,notification,entry.targetId(),ref.kind(),ref.id());
                    }
                }
                jdbc.update("UPDATE lifecycle_content_dispatch SET advance_event_id=NULL WHERE account_id=?",entry.targetId());
                start(entry);
            });
        } else if(ErasedContent.RECEIPT.equals(event.kind())) {
            var receipt=read(event,ErasedContent.Receipt.class);receipt.validate();
            if(!source.equals(receipt.owner()) || !Set.of("search","notification").contains(event.producer())) throw invalid();
            consumer.apply(event,false,() -> {
                lock(receipt.batch().entry());
                String column=event.producer()+"_event_id",ack=event.producer()+"_ack";
                var expected=jdbc.query("SELECT source_kind,source_id FROM lifecycle_erased_content WHERE target_kind='ACCOUNT' AND target_id=? AND "+column+"=? ORDER BY source_kind,source_id",
                        (rs,n)->new ErasedContent.Ref(rs.getString(1),rs.getObject(2,UUID.class)),receipt.batch().entry().targetId(),receipt.commandId());
                var keys=jdbc.query("SELECT aggregate_key FROM durable_outbox WHERE id=? AND kind=? AND destination=?",(rs,n)->rs.getString(1),
                        receipt.commandId(),ErasedContent.ERASE,"lifecycle-"+event.producer());
                if(!expected.equals(receipt.batch().refs()) || keys.size()!=1 || !keys.getFirst().equals(event.aggregateKey())) throw invalid();
                jdbc.update("UPDATE lifecycle_erased_content SET "+ack+"=TRUE WHERE target_kind='ACCOUNT' AND target_id=? AND "+column+"=?",receipt.batch().entry().targetId(),receipt.commandId());
                terminal.getObject().reconcile(receipt.batch().entry());
            });
        } else throw invalid();
    }
    private void lock(TerminalJournal.Entry entry) {
        jdbc.queryForObject("SELECT id FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE",Integer.class);
        terminal.getObject().cleanup(entry);
    }
    private void purge(ErasedContent.Ref ref) {
        // The claimed copy may still be in flight. Permanent recipient fences handle that copy.
        jdbc.update("""
            UPDATE durable_outbox SET status='ERASED',payload='{}',lease_token=NULL,lease_until=NULL,last_error=NULL
            WHERE destination='search' AND kind=? AND aggregate_key=?
            """,ref.kind(),ref.kind()+":"+ref.id());
        jdbc.update("""
            UPDATE durable_outbox SET status='ERASED',payload='{}',lease_token=NULL,lease_until=NULL,last_error=NULL
            WHERE destination='notification' AND kind='NOTIFICATION' AND aggregate_key LIKE ? ESCAPE '!'
            ""","NOTIFICATION:%:"+ref.notificationType().replace("_","!_")+":"+ref.id()+":%");
    }
    private UUID append(String destination,String kind,String key,Object payload) {
        try { return outbox.append("lifecycle-"+destination,kind,key,mapper.writeValueAsString(payload)); }
        catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw invalid(); }
    }
    private <T> T read(DurableEvent event,Class<T> type) {
        try {
            T value=mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readValue(event.payload());
            if(value==null) throw invalid();return value;
        } catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw invalid(); }
    }
    private static String advanceKey(TerminalJournal.Entry entry) { return "ACCOUNT_CONTENT_ADVANCE:"+entry.eventId(); }
    private void requireSource() { if(!Set.of("community","message").contains(source)) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid erased content source delivery"); }
}
