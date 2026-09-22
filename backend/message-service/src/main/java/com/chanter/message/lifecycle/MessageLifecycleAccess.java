package com.chanter.message.lifecycle;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The terminal head is always acquired before social pair locks and content rows. */
public final class MessageLifecycleAccess {
    private final JdbcClient jdbc;
    public MessageLifecycleAccess(JdbcClient jdbc) { this.jdbc=jdbc; }
    public void lock() {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Message terminal mutation requires transaction");
        jdbc.sql("SELECT revision FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE").query(Long.class).single();
    }
    public boolean accountDeleted(UUID user) {
        return user!=null && jdbc.sql("SELECT COUNT(*) FROM lifecycle_terminal_targets WHERE target_kind='ACCOUNT' AND target_id=:id")
                .param("id",user).query(Integer.class).single()!=0;
    }
    public void requireAccount(UUID user) { if(accountDeleted(user)) denied(); }
    public boolean scopeDeleted(String kind,UUID id) {
        if(id==null) return false;
        if(!java.util.List.of("COURSE","CHANNEL").contains(kind)) throw new IllegalArgumentException("Invalid message scope");
        for(String table:java.util.List.of("lifecycle_scope_import","lifecycle_recovery_scope")) {
            if(jdbc.sql("""
                    SELECT EXISTS(SELECT 1 FROM lifecycle_scope_import_ids s JOIN lifecycle_scope_imports h
                    ON h.study_server_id=s.study_server_id AND h.scope_kind=s.scope_kind
                    JOIN lifecycle_terminal_targets t ON t.target_kind='STUDY_SERVER' AND t.target_id=h.study_server_id
                    AND t.revision=h.revision AND t.event_id=h.event_id AND t.digest=h.terminal_digest
                    WHERE h.ready=TRUE AND s.scope_kind=:kind AND s.scope_id=:id)
                    """.replace("lifecycle_scope_import",table)).param("kind",kind).param("id",id).query(Boolean.class).single()) return true;
        }
        return false;
    }
    public void requireScope(String kind,UUID id) { if(scopeDeleted(kind,id)) denied(); }
    public void requireQuestion(UUID id) {
        var question=jdbc.sql("SELECT sender_user_id,channel_id FROM support_questions WHERE id=:id").param("id",id)
                .query((rs,n) -> new UUID[]{rs.getObject(1,UUID.class),rs.getObject(2,UUID.class)}).optional();
        if(question.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Support Question not found");
        requireAccount(question.get()[0]); requireScope("CHANNEL",question.get()[1]);
    }
    private static void denied() { throw new ResponseStatusException(HttpStatus.GONE,"LIFECYCLE_TARGET_DELETED"); }
}
