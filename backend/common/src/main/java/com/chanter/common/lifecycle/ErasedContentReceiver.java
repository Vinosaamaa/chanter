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
        CREATE INDEX lifecycle_erased_content_reverse ON lifecycle_erased_content_fences(source_kind,source_id)
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
        if(!Set.of("search","notification").contains(source) || !event.kind().equals(ErasedContent.ERASE)) throw invalid();
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
            for(var ref:batch.refs()) {
                if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_erased_content_fences WHERE owner=? AND source_kind=? AND source_id=?",
                        Integer.class,event.producer(),ref.kind(),ref.id())==0)
                    jdbc.update("INSERT INTO lifecycle_erased_content_fences VALUES (?,?,?)",event.producer(),ref.kind(),ref.id());
                erase.getObject().apply(ref);
            }
            try {
                var receipt=new ErasedContent.Receipt(event.producer(),event.id(),batch);
                outbox.append("lifecycle-"+event.producer(),ErasedContent.RECEIPT,event.aggregateKey(),mapper.writeValueAsString(receipt));
            } catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw invalid(); }
        });
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid erased content delivery"); }
    @FunctionalInterface public interface Erase { void apply(ErasedContent.Ref ref); }
}
