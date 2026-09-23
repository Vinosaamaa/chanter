package com.chanter.agent.lifecycle;

import com.chanter.common.lifecycle.RecoveryInvalidationStore;
import com.chanter.common.lifecycle.TerminalJournal;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Recovery keeps public writers closed. This receipt proves only the committed native-request invalidation. */
@Service
public class AgentRecoveryInvalidation {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final RecoveryInvalidationStore invalidations;
    public AgentRecoveryInvalidation(JdbcTemplate jdbc,PlatformTransactionManager transactions,Clock clock) {
        this.jdbc=jdbc; tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        invalidations=new RecoveryInvalidationStore(jdbc,clock,"agent");
    }
    public RecoveryInvalidationStore.Receipt invalidate(RecoveryInvalidationStore.Request request) {
        request.validate();
        return tx.execute(status -> {
            var authority=jdbc.queryForObject("SELECT revision,digest FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE",
                    (rs,row) -> new TerminalJournal.Watermark(rs.getLong(1),rs.getString(2)));
            return invalidations.invalidate(request,authority,now -> jdbc.update("""
                UPDATE native_companion_requests SET outcome='REJECTED',evidence_json=NULL
                WHERE outcome IN ('ISSUED','ACCEPTING')
                """));
        });
    }
}
