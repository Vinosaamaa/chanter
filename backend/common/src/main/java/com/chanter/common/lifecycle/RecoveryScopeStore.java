package com.chanter.common.lifecycle;

import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** The fixed recovery-only companion to the current archive store. */
public final class RecoveryScopeStore {
    private final UUID restoreId;
    private final DeletedScopeStore current;
    private final DeletedScopeStore derived;
    private final JdbcTemplate jdbc;
    private final Consumer<TerminalJournal.Entry> reconcile;
    public RecoveryScopeStore(JdbcTemplate jdbc,TransactionTemplate tx,DeletedScopeStore current,UUID restoreId,
            Consumer<TerminalJournal.Entry> authority,Consumer<TerminalJournal.Entry> reconcile) {
        this.restoreId=restoreId; this.current=current; this.jdbc=jdbc; this.reconcile=reconcile;
        this.derived=new DeletedScopeStore(jdbc,tx,authority,this::reconcileVerified,true);
    }
    public UUID restoreId() {
        if(restoreId==null) throw new IllegalArgumentException("Verified recovery runtime required");
        RecoveryScope.requireId(restoreId); return restoreId;
    }
    public String basis(TerminalJournal.Entry entry,String kind) {
        requireCurrent(entry);
        return RecoveryScope.basis(restoreId(),entry,kind,current.scopeDigest(entry,kind));
    }
    private void requireCurrent(TerminalJournal.Entry entry) {
        restoreId();
        if(!current.ready(entry,"COURSE") || !current.ready(entry,"CHANNEL")) throw new IllegalArgumentException("Both current archived kinds must be READY");
    }
    public RecoveryScope.Receipt accept(RecoveryScope.Import request) {
        request.validate(); requireCurrent(request.entry());
        if(!restoreId().equals(request.restoreId()) || !current.scopeDigest(request.entry(),request.page().kind()).equals(request.originalScopeDigest()))
            throw new IllegalArgumentException("Recovery scope binding changed");
        var receipt=derived.accept(new DeletedScope.Import(request.entry(),request.page()),basis(request.entry(),request.page().kind()));
        return new RecoveryScope.Receipt(1,restoreId(),request.recoveryId(),request.originalScopeDigest(),receipt);
    }
    public boolean ready(TerminalJournal.Entry entry,String kind) {
        if(restoreId==null || !current.ready(entry,"COURSE") || !current.ready(entry,"CHANNEL") || !derived.ready(entry,kind)) return false;
        return derived.basis(entry,kind).equals(basis(entry,kind));
    }
    public RecoveryScope.Page page(UUID recoveryId,TerminalJournal.Entry entry,String kind,UUID after,int limit) {
        RecoveryScope.requireId(recoveryId);
        if(!ready(entry,kind)) throw new IllegalArgumentException("Recovery scope is not READY");
        return new RecoveryScope.Page(1,restoreId(),recoveryId,current.scopeDigest(entry,kind),
                derived.page(entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),kind,after,limit));
    }
    public RecoveryScope.Receipt receipt(UUID recoveryId,TerminalJournal.Entry entry,String kind) {
        RecoveryScope.requireId(recoveryId);
        if(!ready(entry,kind)) throw new IllegalArgumentException("Recovery scope is not READY");
        return new RecoveryScope.Receipt(1,restoreId(),recoveryId,current.scopeDigest(entry,kind),derived.receipt(entry,kind));
    }
    private void reconcileVerified(TerminalJournal.Entry entry) {
        for(String kind:java.util.List.of("COURSE","CHANNEL")) {
            if(!ready(entry,kind)) continue;
            if(Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM lifecycle_scope_import_ids c WHERE c.study_server_id=? AND c.scope_kind=?
                        AND NOT EXISTS(SELECT 1 FROM lifecycle_recovery_scope_ids r
                            WHERE r.study_server_id=c.study_server_id AND r.scope_kind=c.scope_kind AND r.scope_id=c.scope_id))
                    """,Boolean.class,entry.targetId(),kind))) throw new IllegalArgumentException("Recovery union omits current archived scope");
        }
        if(ready(entry,"COURSE") && ready(entry,"CHANNEL")) reconcile.accept(entry);
    }
}
