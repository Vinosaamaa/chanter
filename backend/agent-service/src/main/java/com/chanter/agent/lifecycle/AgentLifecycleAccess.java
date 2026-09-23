package com.chanter.agent.lifecycle;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Source writes lock terminal authority before resource, installation, answer or request rows. */
public final class AgentLifecycleAccess {
    private final JdbcClient jdbc;
    public AgentLifecycleAccess(JdbcClient jdbc) { this.jdbc=jdbc; }
    public void lock() {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Agent terminal check requires transaction");
        jdbc.sql("SELECT revision FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE").query(Long.class).single();
    }
    public void require(String kind,UUID id) {
        lock();
        if(!writable(kind,id)) throw deleted();
    }
    private boolean writable(String kind,UUID id) {
        return id==null || jdbc.sql("SELECT COUNT(*) FROM lifecycle_terminal_targets WHERE target_kind=:kind AND target_id=:id")
                .param("kind",kind).param("id",id).query(Integer.class).single()==0;
    }
    public boolean resourceWritable(UUID resource,UUID course,UUID server) {
        lock(); return writable("RESOURCE",resource) && writable("STUDY_SERVER",server) && scopeWritable(course,null);
    }
    public void requireResource(UUID resource) {
        if(!indexedResourceWritable(resource)) throw deleted();
    }
    public boolean indexedResourceWritable(UUID resource) {
        lock();
        if(jdbc.sql("SELECT COUNT(*) FROM resource_index_lifecycle WHERE resource_id=:id AND deleted=TRUE")
                .param("id",resource).query(Integer.class).single()!=0) return false;
        var scope=jdbc.sql("SELECT course_id,study_server_id FROM resource_index_lifecycle WHERE resource_id=:id")
                .param("id",resource).query((rs,row)->new UUID[]{rs.getObject(1,UUID.class),rs.getObject(2,UUID.class)}).optional();
        return resourceWritable(resource,scope.map(ids->ids[0]).orElse(null),scope.map(ids->ids[1]).orElse(null));
    }
    public void requireScope(UUID course,UUID channel) {
        lock(); if(!scopeWritable(course,channel)) throw deleted();
    }
    private boolean scopeWritable(UUID course,UUID channel) {
        if(course==null && channel==null) return true;
        for(String table:List.of("lifecycle_scope_import","lifecycle_recovery_scope")) {
            if(jdbc.sql("""
                    SELECT EXISTS(SELECT 1 FROM lifecycle_scope_import_ids s JOIN lifecycle_scope_imports h
                        ON h.study_server_id=s.study_server_id AND h.scope_kind=s.scope_kind
                        JOIN lifecycle_terminal_targets t ON t.target_kind='STUDY_SERVER' AND t.target_id=h.study_server_id
                            AND t.revision=h.revision AND t.event_id=h.event_id AND t.digest=h.terminal_digest
                        WHERE h.ready=TRUE AND ((s.scope_kind='COURSE' AND s.scope_id=:course) OR (s.scope_kind='CHANNEL' AND s.scope_id=:channel)))
                    """.replace("lifecycle_scope_import",table)).param("course",course).param("channel",channel).query(Boolean.class).single()) return false;
        }
        return true;
    }
    private static ResponseStatusException deleted() { return new ResponseStatusException(HttpStatus.GONE,"LIFECYCLE_TARGET_DELETED"); }
}
