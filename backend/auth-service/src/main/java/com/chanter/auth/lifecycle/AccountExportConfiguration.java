package com.chanter.auth.lifecycle;

import com.chanter.common.events.DurableOutbox;
import com.chanter.common.lifecycle.AccountExportProtocol;
import com.chanter.common.lifecycle.ExportSnapshotConfiguration;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import java.time.Clock;
import java.net.URI;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Import(ExportSnapshotConfiguration.class)
public class AccountExportConfiguration {
    @Bean ExportSourceClient exportSourceClient(ObjectMapper mapper, ExportSnapshotStore snapshots,
            @Value("${COMMUNITY_SERVICE_URL:http://localhost:8082}") String community,
            @Value("${MESSAGE_SERVICE_URL:http://localhost:8083}") String message,
            @Value("${MEDIA_SERVICE_URL:http://localhost:8084}") String media,
            @Value("${AGENT_SERVICE_URL:http://localhost:8085}") String agent,
            @Value("${SEARCH_SERVICE_URL:http://localhost:8088}") String search,
            @Value("${NOTIFICATION_SERVICE_URL:http://localhost:8089}") String notification,
            @Value("${chanter.internal-service-token}") String token) {
        return new ExportSourceClient(Map.of("community", URI.create(community), "message", URI.create(message), "media", URI.create(media),
                "agent", URI.create(agent), "search", URI.create(search), "notification", URI.create(notification)), token, mapper, snapshots);
    }
    @Bean AccountExportJobs accountExportJobs(JdbcTemplate jdbc, PlatformTransactionManager transactions, LifecycleSessionAccess access,
            ExportSnapshotStore snapshots, AuthAccountExport projection, DurableOutbox outbox, AccountExportProtocol protocol) {
        var tx = new TransactionTemplate(transactions);
        tx.setTimeout(5);
        return new AccountExportJobs(jdbc, tx, access, snapshots, projection, outbox, protocol, Clock.systemUTC());
    }
    @Bean HistoryExpiry lifecycleHistoryExpiry(AccountExportJobs jobs) { return new HistoryExpiry(jobs); }
    @Bean ExportDownloadHandles exportDownloadHandles(JdbcTemplate jdbc, PlatformTransactionManager transactions,
            LifecycleSessionAccess access, AccountExportJobs jobs, com.chanter.auth.application.RefreshTokenRepository refreshTokens) {
        var tx = new TransactionTemplate(transactions); tx.setTimeout(5);
        return new ExportDownloadHandles(jdbc, tx, access, jobs, refreshTokens);
    }
    static final class HistoryExpiry {
        private final AccountExportJobs jobs;
        HistoryExpiry(AccountExportJobs jobs) { this.jobs = jobs; }
        @Scheduled(fixedDelayString="${chanter.lifecycle.history-expiry-poll-ms:3600000}")
        public void expire() { jobs.expireHistory(); }
    }
}
