package com.chanter.auth.lifecycle;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class TerminalJournalConfiguration {
    @Bean SourceDeletionJobs sourceDeletionJobs(JdbcTemplate jdbc,PlatformTransactionManager transactions,TerminalJournalStore journal,
            AuthTerminalRecovery auth,com.chanter.common.events.DurableOutbox outbox,com.chanter.common.lifecycle.AccountDeletionProtocol protocol) {
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new SourceDeletionJobs(jdbc,tx,journal,auth,outbox,protocol);
    }
    @Bean TerminalJournalStore terminalJournalStore(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        var tx = new TransactionTemplate(transactions); tx.setTimeout(30);
        return new TerminalJournalStore(jdbc, tx, Clock.systemUTC());
    }
    @Bean AuthTerminalRecovery authTerminalRecovery(JdbcTemplate jdbc, PlatformTransactionManager transactions,
            TerminalJournalStore journal, com.chanter.auth.application.RefreshTokenRepository sessions, AccountExportJobs exports) {
        var tx = new TransactionTemplate(transactions); tx.setTimeout(30);
        return new AuthTerminalRecovery(jdbc, tx, journal, sessions, exports, Clock.systemUTC());
    }
    @Bean AccountDeletionJobs accountDeletionJobs(JdbcTemplate jdbc,PlatformTransactionManager transactions,LifecycleSessionAccess access,
            TerminalJournalStore journal,AuthTerminalRecovery auth,com.chanter.common.events.DurableOutbox outbox,
            com.chanter.common.lifecycle.AccountDeletionProtocol protocol) {
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new AccountDeletionJobs(jdbc,tx,access,journal,auth,outbox,protocol,Clock.systemUTC());
    }
}
