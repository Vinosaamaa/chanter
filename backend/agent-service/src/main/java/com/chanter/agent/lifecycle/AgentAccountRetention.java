package com.chanter.agent.lifecycle;

import com.chanter.common.lifecycle.TerminalReapplyStore;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Removes account attribution while preserving server-owned installations and conservative attempt claims. */
@Component
public final class AgentAccountRetention {
    private final JdbcTemplate jdbc;
    public AgentAccountRetention(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void erase(UUID account) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Agent retention requires terminal transaction");
        jdbc.update("""
            INSERT INTO lifecycle_agent_account_retention(account_id,usage_claims,shared_installs)
            SELECT ?,(SELECT COUNT(*) FROM ai_generation_usage WHERE learner_user_id=?),
              (SELECT COUNT(*) FROM study_assistant_installs WHERE installed_by_user_id=?)
            WHERE NOT EXISTS(SELECT 1 FROM lifecycle_agent_account_retention WHERE account_id=?)
            """,account,account,account,account);
        jdbc.update("DELETE FROM native_companion_requests WHERE user_id=?",account);
        jdbc.update("""
            UPDATE ai_generation_usage SET learner_user_id=NULL,provider_request_id=NULL,
              outcome=CASE WHEN outcome='RESERVED' THEN 'UNKNOWN' ELSE outcome END
            WHERE learner_user_id=?
            """,account);
        jdbc.update("UPDATE study_assistant_installs SET installed_by_user_id=NULL WHERE installed_by_user_id=?",account);
    }
    public TerminalReapplyStore.Cleanup disposition(UUID account) {
        return jdbc.queryForObject("SELECT usage_claims+shared_installs FROM lifecycle_agent_account_retention WHERE account_id=?",Long.class,account)>0
                ? TerminalReapplyStore.Cleanup.PRESERVED : TerminalReapplyStore.Cleanup.COMPLETE;
    }
}
