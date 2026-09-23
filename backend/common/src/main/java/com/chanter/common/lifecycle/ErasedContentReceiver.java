package com.chanter.common.lifecycle;

import com.chanter.common.events.*;
import com.fasterxml.jackson.databind.*;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Permanent content fences and their acknowledgement commit in the same recipient transaction. */
public final class ErasedContentReceiver {
    public static final String SCHEMA="""
        CREATE TABLE lifecycle_erased_content_fences (
            owner VARCHAR(16) NOT NULL,source_kind VARCHAR(24) NOT NULL,source_id UUID NOT NULL,
            PRIMARY KEY(owner,source_kind,source_id)
        );
        CREATE INDEX lifecycle_erased_content_reverse ON lifecycle_erased_content_fences(source_kind,source_id);
        CREATE TABLE lifecycle_content_commands (
            command_id UUID PRIMARY KEY,owner VARCHAR(16) NOT NULL,account_id UUID NOT NULL,
            terminal_digest VARCHAR(64) NOT NULL,content_count INTEGER NOT NULL,payload_digest VARCHAR(64) NOT NULL
        );
        CREATE INDEX lifecycle_content_commands_account ON lifecycle_content_commands(account_id,owner,terminal_digest);
        CREATE TABLE lifecycle_content_received_final (
            account_id UUID NOT NULL,owner VARCHAR(16) NOT NULL,terminal_digest VARCHAR(64) NOT NULL,
            content_count BIGINT NOT NULL,batch_count BIGINT NOT NULL,PRIMARY KEY(account_id,owner)
        )
        """;
    private final String source;
    private final JdbcTemplate jdbc;
    private final DurableConsumer consumer;
    private final DurableOutbox outbox;
    private final ObjectMapper mapper;
    private final ObjectProvider<TerminalReapplyStore> terminal;
    private final ObjectProvider<Erase> erase;
    public ErasedContentReceiver(String source,JdbcTemplate jdbc,TransactionTemplate tx,DurableOutbox outbox,ObjectMapper mapper,
            ObjectProvider<TerminalReapplyStore> terminal,ObjectProvider<Erase> erase) {
        this.source=source;this.jdbc=jdbc;this.consumer=new DurableConsumer(jdbc,tx);this.outbox=outbox;
        this.mapper=mapper;this.terminal=terminal;this.erase=erase;
    }
    public void accept(DurableEvent event) {
        event.validate();
        if(!Set.of("search","notification").contains(source)) throw invalid();
        if(event.kind().equals(ErasedContent.FINAL)) { finish(event);return; }
        if(!event.kind().equals(ErasedContent.ERASE)) throw invalid();
        final ErasedContent.Batch batch;
        try {
            batch=mapper.readerFor(ErasedContent.Batch.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readValue(event.payload());
        } catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw invalid(); }
        if(batch==null) throw invalid();
        batch.validate(event.producer());
        // Aggregate identity is fixed by the originating command, including when transport retries it.
        if(!event.aggregateKey().startsWith("ACCOUNT_CONTENT:"+batch.entry().eventId()+":")) throw invalid();
        String suffix=event.aggregateKey().substring(("ACCOUNT_CONTENT:"+batch.entry().eventId()+":").length());
        try { if(!batch.key(java.util.UUID.fromString(suffix)).equals(event.aggregateKey())) throw invalid(); }
        catch(IllegalArgumentException failure) { throw invalid(); }
        consumer.apply(event,false,() -> {
            jdbc.queryForObject("SELECT id FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE",Integer.class);
            terminal.getObject().cleanup(batch.entry());
            if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_content_received_final WHERE account_id=? AND owner=?",Integer.class,batch.entry().targetId(),event.producer())!=0) throw invalid();
            if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_content_commands WHERE command_id=?",Integer.class,event.id())!=0) throw invalid();
            for(var ref:batch.refs()) {
                if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_erased_content_fences WHERE owner=? AND source_kind=? AND source_id=?",
                        Integer.class,event.producer(),ref.kind(),ref.id())==0)
                    jdbc.update("INSERT INTO lifecycle_erased_content_fences VALUES (?,?,?)",event.producer(),ref.kind(),ref.id());
                erase.getObject().apply(ref);
            }
            jdbc.update("INSERT INTO lifecycle_content_commands VALUES (?,?,?,?,?,?)",event.id(),event.producer(),batch.entry().targetId(),batch.entry().digest(),batch.refs().size(),ErasedContent.digest(batch));
            try {
                var receipt=new ErasedContent.Receipt(event.producer(),event.id(),batch);
                outbox.append("lifecycle-"+event.producer(),ErasedContent.RECEIPT,event.aggregateKey(),mapper.writeValueAsString(receipt));
            } catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw invalid(); }
        });
    }
    public boolean complete(TerminalJournal.Entry entry) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_content_received_final WHERE account_id=? AND terminal_digest=? AND owner IN ('community','message','media','agent')",Integer.class,entry.targetId(),entry.digest())==ErasedContent.OWNERS.size();
    }
    private void finish(DurableEvent event) {
        final ErasedContent.Completion completion;
        try { completion=mapper.readerFor(ErasedContent.Completion.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readValue(event.payload()); }
        catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw invalid(); }
        if(completion==null) throw invalid();completion.validate();
        if(!event.producer().equals(completion.owner()) || !completion.key().equals(event.aggregateKey())) throw invalid();
        consumer.apply(event,false,() -> {
            jdbc.queryForObject("SELECT id FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE",Integer.class);
            terminal.getObject().cleanup(completion.entry());
            long count=jdbc.queryForObject("SELECT COALESCE(SUM(content_count),0) FROM lifecycle_content_commands WHERE account_id=? AND owner=? AND terminal_digest=?",Long.class,
                    completion.entry().targetId(),event.producer(),completion.entry().digest());
            long batches=jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_content_commands WHERE account_id=? AND owner=? AND terminal_digest=?",Long.class,
                    completion.entry().targetId(),event.producer(),completion.entry().digest());
            if(count!=completion.contentCount() || batches!=completion.batchCount()) throw invalid();
            var prior=jdbc.query("SELECT terminal_digest,content_count,batch_count FROM lifecycle_content_received_final WHERE account_id=? AND owner=?",
                    (rs,n) -> new FinalState(rs.getString(1),rs.getLong(2),rs.getLong(3)),completion.entry().targetId(),event.producer());
            var expected=new FinalState(completion.entry().digest(),count,batches);
            if(!prior.isEmpty() && !prior.getFirst().equals(expected)) throw invalid();
            if(prior.isEmpty()) jdbc.update("INSERT INTO lifecycle_content_received_final VALUES (?,?,?,?,?)",completion.entry().targetId(),event.producer(),completion.entry().digest(),count,batches);
            try { outbox.append("lifecycle-"+event.producer(),ErasedContent.COMPLETE,event.aggregateKey(),mapper.writeValueAsString(new ErasedContent.FinalReceipt(event.id(),completion))); }
            catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw invalid(); }
            terminal.getObject().reconcile(completion.entry());
        });
    }
    private record FinalState(String digest,long count,long batches) { }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid erased content delivery"); }
    @FunctionalInterface public interface Erase { void apply(ErasedContent.Ref ref); }
}
