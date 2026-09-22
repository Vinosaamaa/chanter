package com.chanter.common.lifecycle;

import com.chanter.common.events.DurableEvent;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;

/** Fixed commands on existing private durable delivery. The journal digest is integrity, not a signature. */
public final class AccountDeletionProtocol {
    public static final String PREPARE="ACCOUNT_DELETE_PREPARE";
    public static final String RELEASE="ACCOUNT_DELETE_RELEASE";
    public static final String TERMINAL="LIFECYCLE_TERMINAL_DELETE";
    public static final String RECEIPT="ACCOUNT_DELETE_RECEIPT";
    private static final Set<String> COMMANDS=Set.of(PREPARE,RELEASE,TERMINAL);
    private final ObjectMapper mapper;
    public AccountDeletionProtocol(ObjectMapper mapper) {
        this.mapper=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    public static boolean command(String kind) { return COMMANDS.contains(kind); }
    public Preparation preparation(DurableEvent event) {
        requireAuth(event);
        if(!Set.of(PREPARE,RELEASE).contains(event.kind())) throw invalid();
        var command=read(event.payload(),Preparation.class); command.validate();
        if(!key("ACCOUNT",command.accountId()).equals(event.aggregateKey())) throw invalid();
        return command;
    }
    public Terminal terminal(DurableEvent event) {
        requireAuth(event);
        if(!TERMINAL.equals(event.kind())) throw invalid();
        var command=read(event.payload(),Terminal.class); command.validate();
        if(!key(command.entry().targetKind(),command.entry().targetId()).equals(event.aggregateKey())) throw invalid();
        return command;
    }
    public Receipt receipt(DurableEvent event) {
        event.validate();
        if(!RECEIPT.equals(event.kind())) throw invalid();
        var receipt=read(event.payload(),Receipt.class); receipt.validate();
        if(!receipt.source().equals(event.producer()) || !key(receipt.targetKind(),receipt.targetId()).equals(event.aggregateKey())) throw invalid();
        return receipt;
    }
    public String encode(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch(java.io.IOException invalid) { throw invalid(); }
    }
    private <T> T read(String json,Class<T> type) {
        try { T value=mapper.readValue(json,type); if(value==null) throw invalid(); return value; }
        catch(java.io.IOException invalid) { throw invalid(); }
    }
    private static void requireAuth(DurableEvent event) { event.validate(); if(!"auth".equals(event.producer())) throw invalid(); }
    private static void requireId(UUID id) { if(id==null || id.equals(new UUID(0,0))) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid account deletion event"); }
    public static String key(String kind,UUID target) { TerminalJournal.requireTarget(kind,target); return "terminal:"+kind+":"+target; }

    public record Preparation(UUID jobId,UUID accountId) {
        public void validate() { requireId(jobId); TerminalJournal.requireTarget("ACCOUNT",accountId); }
    }
    public record Terminal(UUID jobId,TerminalJournal.Entry entry) {
        public void validate() { requireId(jobId); if(entry==null) throw invalid(); entry.validate(); }
    }
    public record Receipt(UUID jobId,String source,String targetKind,UUID targetId,String state,Long terminalRevision,String terminalDigest) {
        public void validate() {
            requireId(jobId); TerminalJournal.requireTarget(targetKind,targetId);
            if(source==null || !AccountExportProtocol.SOURCES.contains(source) || state==null) throw invalid();
            if(Set.of("PREPARED","BLOCKED_OWNERSHIP","RELEASED").contains(state)) {
                if(!"community".equals(source) || !"ACCOUNT".equals(targetKind) || terminalRevision!=null || terminalDigest!=null) throw invalid();
            } else if(!Set.of("PENDING","PRESERVED","COMPLETE").contains(state) || terminalRevision==null || terminalRevision<1
                    || terminalDigest==null || !terminalDigest.matches("[a-f0-9]{64}")) throw invalid();
        }
    }
}
