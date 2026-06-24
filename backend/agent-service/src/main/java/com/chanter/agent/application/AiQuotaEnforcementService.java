package com.chanter.agent.application;

import com.chanter.agent.domain.AiInvocationCounts;
import java.sql.Connection;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiQuotaEnforcementService {

    private final StudyServerSaasPlanClient saasPlanClient;
    private final AiUsageMetricsRepository metricsRepository;
    private final JdbcClient jdbcClient;
    private final DataSource dataSource;
    private volatile Boolean postgresDatabase;

    public AiQuotaEnforcementService(
            StudyServerSaasPlanClient saasPlanClient,
            AiUsageMetricsRepository metricsRepository,
            JdbcClient jdbcClient,
            DataSource dataSource
    ) {
        this.saasPlanClient = saasPlanClient;
        this.metricsRepository = metricsRepository;
        this.jdbcClient = jdbcClient;
        this.dataSource = dataSource;
    }

    public void requireQuotaAvailable(UUID studyServerId) {
        assertQuotaAvailable(studyServerId);
    }

    @Transactional
    public void requireQuotaAvailableUnderLock(UUID studyServerId) {
        acquireStudyServerLock(studyServerId);
        assertQuotaAvailable(studyServerId);
    }

    private void assertQuotaAvailable(UUID studyServerId) {
        StudyServerSaasPlanClient.StudyServerSaasPlan plan = saasPlanClient.fetchPlan(studyServerId);
        AiInvocationCounts usage = metricsRepository.findInvocationCounts(studyServerId);
        if (usage.totalInvocations() >= plan.aiInvocationLimit()) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "AI Study Assistant quota exhausted for the "
                            + plan.planTier()
                            + " plan. Upgrade the Study Server SaaS Plan to continue."
            );
        }
    }

    private void acquireStudyServerLock(UUID studyServerId) {
        if (!isPostgresDatabase()) {
            return;
        }

        jdbcClient.sql("SELECT pg_advisory_xact_lock(:msb, :lsb)")
                .param("msb", studyServerId.getMostSignificantBits())
                .param("lsb", studyServerId.getLeastSignificantBits())
                .query(Long.class)
                .single();
    }

    private boolean isPostgresDatabase() {
        if (postgresDatabase == null) {
            synchronized (this) {
                if (postgresDatabase == null) {
                    postgresDatabase = detectPostgresDatabase();
                }
            }
        }
        return postgresDatabase;
    }

    private boolean detectPostgresDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        } catch (Exception exception) {
            return false;
        }
    }
}
