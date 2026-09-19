package com.chanter.auth.lifecycle;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class TerminalJournalConfiguration {
    @Bean TerminalJournalStore terminalJournalStore(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        return new TerminalJournalStore(jdbc, new TransactionTemplate(transactions), Clock.systemUTC());
    }
}
