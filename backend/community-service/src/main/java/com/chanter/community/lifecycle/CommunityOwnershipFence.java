package com.chanter.community.lifecycle;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Shared by server creation, ownership transfer and account closure. Call before locking server rows. */
@Repository
public class CommunityOwnershipFence {
    private final JdbcTemplate jdbc;
    public CommunityOwnershipFence(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public void requireWritable(UUID account) {
        var state=lock(account);
        if(state.terminal()) throw new ResponseStatusException(HttpStatus.GONE,"ACCOUNT_DELETED");
        if(state.job()!=null) throw new ResponseStatusException(HttpStatus.CONFLICT,"ACCOUNT_DELETION_PREPARED");
    }

    /** Owning durable consumer commits the preparation receipt in this same transaction. */
    public Preparation prepare(UUID account,UUID job) {
        requireId(job);
        var state=lock(account);
        if(state.terminal()) throw new ResponseStatusException(HttpStatus.GONE,"ACCOUNT_DELETED");
        if(state.job()!=null) {
            if(!state.job().equals(job)) throw new ResponseStatusException(HttpStatus.CONFLICT,"ACCOUNT_DELETION_ALREADY_PREPARED");
            return Preparation.PREPARED;
        }
        if(jdbc.queryForObject("SELECT COUNT(*) FROM study_servers WHERE owner_user_id=?",Long.class,account)>0)
            return Preparation.BLOCKED_OWNERSHIP;
        jdbc.update("UPDATE lifecycle_account_ownership SET preparation_job=? WHERE account_id=?",job,account);
        return Preparation.PREPARED;
    }

    /** Delayed cancellation cannot reopen a newer preparation or a terminal account. */
    public boolean release(UUID account,UUID job) {
        requireId(job); lock(account);
        return jdbc.update("UPDATE lifecycle_account_ownership SET preparation_job=NULL WHERE account_id=? AND preparation_job=? AND terminal=FALSE",account,job)==1;
    }

    public void close(UUID account) {
        lock(account);
        jdbc.update("UPDATE lifecycle_account_ownership SET preparation_job=NULL,terminal=TRUE WHERE account_id=?",account);
    }

    private State lock(UUID account) {
        requireId(account);
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Ownership requires a source transaction");
        jdbc.update("INSERT INTO lifecycle_account_ownership(account_id) VALUES (?) ON CONFLICT DO NOTHING",account);
        return jdbc.queryForObject("SELECT preparation_job,terminal FROM lifecycle_account_ownership WHERE account_id=? FOR UPDATE",
                (rs,row) -> new State(rs.getObject(1,UUID.class),rs.getBoolean(2)),account);
    }
    private static void requireId(UUID id) {
        if(id==null || id.equals(new UUID(0,0))) throw new IllegalArgumentException("Invalid ownership identity");
    }
    public enum Preparation { PREPARED, BLOCKED_OWNERSHIP }
    private record State(UUID job,boolean terminal) { }
}
