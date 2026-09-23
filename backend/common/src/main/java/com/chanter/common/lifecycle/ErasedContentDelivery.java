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
        CREATE TABLE lifecycle_content_dispatch (account_id UUID PRIMARY KEY,advance_event_id UUID,payload_cutoff BIGINT NOT NULL);
        CREATE TABLE lifecycle_content_redactions (event_id UUID PRIMARY KEY);
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
            jdbc.update("INSERT INTO lifecycle_content_dispatch(account_id,payload_cutoff) SELECT ?,COALESCE(MAX(revision),0) FROM durable_outbox",entry.targetId());
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
                var ready=new ArrayList<ErasedContent.Ref>();
                for(var ref:refs) {
                    boolean complete=purge(entry,ref);
                    if(complete) ready.add(ref);
                    // Shared previews have their own bounded retained-event page. Continue through the same outbox.
                    if(!complete || ref.kind().equals("QUESTION_PREVIEW")) break;
                }
                if(!ready.isEmpty()) {
                    var batch=new ErasedContent.Batch(entry,ready);batch.validate(source);
                    String key=batch.key(UUID.randomUUID());
                    UUID search=append("search",ErasedContent.ERASE,key,batch),notification=append("notification",ErasedContent.ERASE,key,batch);
                    for(var ref:ready) {
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
    private boolean purge(TerminalJournal.Entry entry,ErasedContent.Ref ref) {
        if(ref.kind().equals("QUESTION_PREVIEW")) return neutralizePreview(entry,ref);
        // The claimed copy may still be in flight. Permanent recipient fences handle that copy.
        jdbc.update("""
            UPDATE durable_outbox SET status='ERASED',payload='{}',lease_token=NULL,lease_until=NULL,last_error=NULL
            WHERE destination='search' AND kind=? AND aggregate_key=?
            """,ref.kind(),ref.kind()+":"+ref.id());
        jdbc.update("""
            UPDATE durable_outbox SET status='ERASED',payload='{}',lease_token=NULL,lease_until=NULL,last_error=NULL
            WHERE destination='notification' AND kind='NOTIFICATION' AND aggregate_key LIKE ? ESCAPE '!'
            ""","NOTIFICATION:%:"+ref.notificationType().replace("_","!_")+":"+ref.id()+":%");
        return true;
    }
    private boolean neutralizePreview(TerminalJournal.Entry entry,ErasedContent.Ref ref) {
        long cutoff=jdbc.queryForObject("SELECT payload_cutoff FROM lifecycle_content_dispatch WHERE account_id=?",Long.class,entry.targetId());
        String predicate="""
            destination='notification' AND kind='NOTIFICATION' AND aggregate_key LIKE ? ESCAPE '!'
            AND revision<=? AND payload<>'{}'
            AND NOT EXISTS(SELECT 1 FROM lifecycle_content_redactions r WHERE r.event_id=durable_outbox.id)
            """;
        String key="NOTIFICATION:%:SUPPORT!_QUESTION:"+ref.id()+":SUPPORT!_QUESTION!_ANSWERED";
        var rows=jdbc.query("SELECT id,aggregate_key,payload FROM durable_outbox WHERE "+predicate+" ORDER BY revision LIMIT 64 FOR UPDATE",
                (rs,n)->new Retained(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)),key,cutoff);
        for(var row:rows) {
            try {
                var value=mapper.readerFor(new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {})
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .<Map<String,Object>>readValue(row.payload());
                if(value==null || !Set.of("userId","kind","filterBucket","title","bodyPreview","courseLabel","href","sourceType","sourceId","studyServerId","courseId","cohortId","channelId").containsAll(value.keySet())
                        || !"SUPPORT_QUESTION".equals(value.get("sourceType")) || !"SUPPORT_QUESTION_ANSWERED".equals(value.get("kind"))
                        || !ref.id().toString().equals(value.get("sourceId"))
                        || !row.key().equals(NotificationEventWriter.aggregateKey(UUID.fromString(String.valueOf(value.get("userId"))),"SUPPORT_QUESTION",ref.id(),"SUPPORT_QUESTION_ANSWERED"))) throw invalid();
                jdbc.update("UPDATE durable_outbox SET payload=? WHERE id=?",mapper.writeValueAsString(NotificationEventWriter.neutralQuestionUpdate(value)),row.id());
                jdbc.update("INSERT INTO lifecycle_content_redactions VALUES (?)",row.id());
            } catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw invalid(); }
        }
        return !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM durable_outbox WHERE "+predicate+")",Boolean.class,key,cutoff));
    }
    private record Retained(UUID id,String key,String payload) { }
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
