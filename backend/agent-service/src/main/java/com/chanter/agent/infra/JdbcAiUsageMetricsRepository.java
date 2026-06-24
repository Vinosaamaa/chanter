package com.chanter.agent.infra;

import com.chanter.agent.application.AiUsageMetricsRepository;
import com.chanter.agent.domain.AiInvocationCounts;
import com.chanter.agent.domain.InvocationType;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAiUsageMetricsRepository implements AiUsageMetricsRepository {

    private final JdbcClient jdbcClient;

    public JdbcAiUsageMetricsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public AiInvocationCounts findInvocationCounts(UUID studyServerId) {
        int totalInvocations = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM study_assistant_audit_records
                        WHERE study_server_id = :studyServerId
                        """)
                .param("studyServerId", studyServerId)
                .query(Integer.class)
                .single();

        int lowConfidenceHandoffs = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM study_assistant_audit_records
                        WHERE study_server_id = :studyServerId
                        AND invocation_type = :handoffType
                        """)
                .param("studyServerId", studyServerId)
                .param("handoffType", InvocationType.LOW_CONFIDENCE_HANDOFF.name())
                .query(Integer.class)
                .single();

        return new AiInvocationCounts(totalInvocations, lowConfidenceHandoffs);
    }
}
