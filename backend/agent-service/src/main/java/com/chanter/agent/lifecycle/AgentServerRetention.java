package com.chanter.agent.lifecycle;

import com.chanter.common.lifecycle.TerminalReapplyStore;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Keeps conservative accounting for a deleted server without native request or account attribution. */
@Component
public final class AgentServerRetention {
    private final JdbcTemplate jdbc;
    public AgentServerRetention(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public void erase(UUID server,String scope) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Server retention requires transaction");
        if(!Set.of("lifecycle_scope_import_ids","lifecycle_recovery_scope_ids").contains(scope)) throw new IllegalArgumentException("Invalid server scope");
        String match="study_server_id=? OR id IN (SELECT id FROM native_companion_requests WHERE channel_id IN (SELECT scope_id FROM "+scope+" WHERE study_server_id=? AND scope_kind='CHANNEL'))";
        jdbc.update("INSERT INTO lifecycle_agent_server_retention(study_server_id,usage_claims) SELECT ?,(SELECT COUNT(*) FROM ai_generation_usage WHERE "+match+") WHERE NOT EXISTS(SELECT 1 FROM lifecycle_agent_server_retention WHERE study_server_id=?)",
                server,server,server,server);
        jdbc.update("UPDATE ai_generation_usage SET learner_user_id=NULL,provider_request_id=NULL,outcome=CASE WHEN outcome='RESERVED' THEN 'UNKNOWN' ELSE outcome END WHERE "+match,server,server);
        jdbc.update("DELETE FROM native_companion_requests WHERE id IN (SELECT id FROM ai_generation_usage WHERE study_server_id=?) OR channel_id IN (SELECT scope_id FROM "+scope+" WHERE study_server_id=? AND scope_kind='CHANNEL')",server,server);
    }
    public TerminalReapplyStore.Cleanup disposition(UUID server) {
        return jdbc.queryForObject("SELECT usage_claims FROM lifecycle_agent_server_retention WHERE study_server_id=?",Long.class,server)>0
                ? TerminalReapplyStore.Cleanup.PRESERVED : TerminalReapplyStore.Cleanup.COMPLETE;
    }
}
