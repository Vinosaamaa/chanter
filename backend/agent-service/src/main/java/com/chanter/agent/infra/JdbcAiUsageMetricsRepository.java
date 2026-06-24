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
        return jdbcClient.sql("""
                        SELECT
                            COUNT(*) AS total_invocations,
                            COUNT(*) FILTER (
                                WHERE invocation_type = :handoffType
                            ) AS low_confidence_handoffs
                        FROM study_assistant_audit_records
                        WHERE study_server_id = :studyServerId
                        """)
                .param("studyServerId", studyServerId)
                .param("handoffType", InvocationType.LOW_CONFIDENCE_HANDOFF.name())
                .query((resultSet, rowNum) -> new AiInvocationCounts(
                        resultSet.getInt("total_invocations"),
                        resultSet.getInt("low_confidence_handoffs")
                ))
                .single();
    }
}
