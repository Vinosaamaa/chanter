package com.chanter.common.lifecycle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Historical scope from one verified restored database; never replaces the current archive. */
public final class RecoveryScope {
    private RecoveryScope() { }
    public static String basis(UUID restoreId,TerminalJournal.Entry entry,String kind,String originalScopeDigest) {
        requireId(restoreId); entry.validate(); DeletedScope.requireKind(kind);
        if(!"STUDY_SERVER".equals(entry.targetKind()) || originalScopeDigest==null || !originalScopeDigest.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid recovery scope authority");
        String value="deleted-study-server-recovery-scope\n1\n"+restoreId+"\n"+entry.digest()+"\n"+kind+"\n"+originalScopeDigest+"\n";
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static void requireId(UUID id) {
        if(id==null || DeletedScope.START.equals(id)) throw new IllegalArgumentException("Invalid recovery identity");
    }
    public record Derive(UUID recoveryId,TerminalJournal.Entry entry) {
        public void validate() {
            requireId(recoveryId);
            if(entry==null) throw new IllegalArgumentException("Missing terminal entry");
            entry.validate(); if(!"STUDY_SERVER".equals(entry.targetKind())) throw new IllegalArgumentException("Invalid scope target");
        }
    }
    public record Import(UUID restoreId,UUID recoveryId,String originalScopeDigest,TerminalJournal.Entry entry,DeletedScope.Page page) {
        public void validate() {
            requireId(restoreId); requireId(recoveryId);
            new DeletedScope.Import(entry,page).validate(); basis(restoreId,entry,page.kind(),originalScopeDigest);
        }
    }
    public record Read(UUID recoveryId,TerminalJournal.Entry entry,String kind,UUID after,int limit) {
        public void validate() {
            new Derive(recoveryId,entry).validate(); DeletedScope.requireKind(kind);
            if(after==null || limit<1 || limit>DeletedScope.MAX_PAGE) throw new IllegalArgumentException("Invalid recovery scope page");
        }
    }
    public record Receipt(int schemaVersion,UUID restoreId,UUID recoveryId,String originalScopeDigest,DeletedScope.Receipt scope) { }
    public record Page(int schemaVersion,UUID restoreId,UUID recoveryId,String originalScopeDigest,DeletedScope.Page page) { }
}
