package com.chanter.common.lifecycle;

import com.chanter.common.events.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** One current-scope page per existing outbox command. Recovery archive relay remains separate. */
public final class DeletedScopeDelivery {
    public static final String ADVANCE="DELETED_SCOPE_ADVANCE",IMPORT="DELETED_SCOPE_IMPORT",READY="DELETED_SCOPE_READY";
    public static final Set<String> DESTINATIONS=Set.of("message","media","agent","search","notification");
    public static final String SCHEMA="""
        CREATE TABLE lifecycle_scope_delivery (
            study_server_id UUID NOT NULL,scope_kind VARCHAR(8) NOT NULL,source VARCHAR(16) NOT NULL,
            terminal_digest VARCHAR(64) NOT NULL,scope_digest VARCHAR(64) NOT NULL,total_count BIGINT NOT NULL,
            PRIMARY KEY(study_server_id,scope_kind,source)
        )
        """;
    private final String source;
    private final JdbcTemplate jdbc;
    private final DurableConsumer consumer;
    private final DurableOutbox outbox;
    private final ObjectMapper mapper;
    private final ObjectProvider<Source> pages;
    private final ObjectProvider<DeletedScopeStore> imports;
    private final ObjectProvider<TerminalReapplyStore> terminal;
    private final boolean recovery;
    public DeletedScopeDelivery(String source,JdbcTemplate jdbc,TransactionTemplate tx,DurableOutbox outbox,ObjectMapper mapper,
            ObjectProvider<Source> pages,ObjectProvider<DeletedScopeStore> imports,ObjectProvider<TerminalReapplyStore> terminal,boolean recovery) {
        this.source=source; this.jdbc=jdbc; this.consumer=new DurableConsumer(jdbc,tx); this.outbox=outbox; this.mapper=mapper;
        this.pages=pages; this.imports=imports; this.terminal=terminal; this.recovery=recovery;
    }
    public static boolean command(String kind) { return Set.of(ADVANCE,IMPORT,READY).contains(kind); }
    public boolean complete(TerminalJournal.Entry entry) {
        entry.validate();
        if(!source.equals("community") || !entry.targetKind().equals("STUDY_SERVER")) throw invalid();
        return recovery || jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_scope_delivery WHERE study_server_id=? AND terminal_digest=?",Integer.class,
                entry.targetId(),entry.digest())==2*DESTINATIONS.size();
    }
    public void start(TerminalJournal.Entry entry) {
        if(recovery) return;
        if(!source.equals("community") || !entry.targetKind().equals("STUDY_SERVER")) throw invalid();
        entry.validate();
        for(String kind:List.of("COURSE","CHANNEL")) {
            var request=new Advance(entry,kind,DeletedScope.START);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind=? AND aggregate_key=?",Integer.class,ADVANCE,request.key())==0)
                append("community",ADVANCE,request.key(),request);
        }
    }
    public void accept(DurableEvent event) {
        event.validate();
        if(recovery) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Ordinary scope delivery is disabled during recovery");
        switch(event.kind()) {
            case ADVANCE -> {
                Advance request=read(event,Advance.class); request.validate();
                if(!source.equals("community") || !event.producer().equals("community") || !event.aggregateKey().equals(request.key())) throw invalid();
                consumer.apply(event,false,() -> {
                    var page=pages.getObject().page(request.entry(),request.kind(),request.after()); page.requireEntry(request.entry());
                    var payload=new DeletedScope.Import(request.entry(),page);
                    for(String destination:DESTINATIONS) append(destination,IMPORT,request.key(),payload);
                    if(page.nextAfter()!=null) {
                        var next=new Advance(request.entry(),request.kind(),page.nextAfter()); append("community",ADVANCE,next.key(),next);
                    }
                });
            }
            case IMPORT -> {
                DeletedScope.Import request=read(event,DeletedScope.Import.class); request.validate();
                if(!DESTINATIONS.contains(source) || !event.producer().equals("community") || !event.aggregateKey().equals(key(request.entry(),request.page().kind(),request.page().after()))) throw invalid();
                consumer.apply(event,false,() -> {
                    var receipt=imports.getObject().accept(request);
                    if(receipt.ready()) append("community",READY,receiptKey(request.entry(),receipt.kind()),new Ready(request.entry(),receipt));
                });
            }
            case READY -> {
                Ready request=read(event,Ready.class); request.validate();
                if(!source.equals("community") || !DESTINATIONS.contains(event.producer()) || !event.aggregateKey().equals(receiptKey(request.entry(),request.receipt().kind()))) throw invalid();
                consumer.apply(event,false,() -> {
                    terminal.getObject().cleanup(request.entry());
                    var expected=pages.getObject().page(request.entry(),request.receipt().kind(),DeletedScope.START);
                    if(expected.totalCount()!=request.receipt().totalCount() || !expected.scopeDigest().equals(request.receipt().scopeDigest())) throw invalid();
                    var prior=jdbc.query("SELECT terminal_digest,scope_digest,total_count FROM lifecycle_scope_delivery WHERE study_server_id=? AND scope_kind=? AND source=?",
                            (rs,n) -> List.of(rs.getString(1),rs.getString(2),Long.toString(rs.getLong(3))),request.entry().targetId(),request.receipt().kind(),event.producer());
                    if(prior.isEmpty()) jdbc.update("INSERT INTO lifecycle_scope_delivery(study_server_id,scope_kind,source,terminal_digest,scope_digest,total_count) VALUES (?,?,?,?,?,?)",
                            request.entry().targetId(),request.receipt().kind(),event.producer(),request.entry().digest(),expected.scopeDigest(),expected.totalCount());
                    else if(!prior.getFirst().equals(List.of(request.entry().digest(),expected.scopeDigest(),Long.toString(expected.totalCount())))) throw invalid();
                    terminal.getObject().reconcile(request.entry());
                });
            }
            default -> throw invalid();
        }
    }
    private void append(String destination,String kind,String key,Object payload) {
        try { outbox.append("lifecycle-"+destination,kind,key,mapper.writeValueAsString(payload)); }
        catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw invalid(); }
    }
    private <T> T read(DurableEvent event,Class<T> type) {
        try {
            T result=mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readValue(event.payload());
            if(result==null) throw invalid(); return result;
        } catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw invalid(); }
    }
    private static String key(TerminalJournal.Entry entry,String kind,UUID after) { return "DELETED_SCOPE:"+entry.targetId()+":"+kind+":"+after; }
    private static String receiptKey(TerminalJournal.Entry entry,String kind) { return "DELETED_SCOPE_READY:"+entry.targetId()+":"+kind; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid scope delivery"); }
    public record Advance(TerminalJournal.Entry entry,String kind,UUID after) {
        public void validate() { if(entry==null || after==null) throw invalid(); entry.validate(); DeletedScope.requireKind(kind); if(!entry.targetKind().equals("STUDY_SERVER")) throw invalid(); }
        public String key() { validate(); return DeletedScopeDelivery.key(entry,kind,after); }
    }
    public record Ready(TerminalJournal.Entry entry,DeletedScope.Receipt receipt) {
        public void validate() {
            if(entry==null || receipt==null) throw invalid(); entry.validate(); DeletedScope.requireKind(receipt.kind());
            if(!entry.targetKind().equals("STUDY_SERVER") || receipt.schemaVersion()!=1 || !receipt.ready() || receipt.totalCount()<0 || receipt.after()==null
                    || receipt.receivedCount()!=receipt.totalCount() || !entry.targetId().equals(receipt.studyServerId())
                    || entry.revision()!=receipt.terminalRevision() || !entry.eventId().equals(receipt.terminalEventId()) || !entry.digest().equals(receipt.terminalDigest())) throw invalid();
        }
    }
    @FunctionalInterface public interface Source { DeletedScope.Page page(TerminalJournal.Entry entry,String kind,UUID after); }
}
