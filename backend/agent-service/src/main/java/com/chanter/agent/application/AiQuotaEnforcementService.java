package com.chanter.agent.application;

import com.chanter.agent.domain.AiInvocationCounts;
import com.chanter.agent.domain.AiUsageMetrics;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiQuotaEnforcementService {

    private final StudyServerSaasPlanClient saasPlanClient;
    private final AiUsageMetricsRepository metricsRepository;

    public AiQuotaEnforcementService(
            StudyServerSaasPlanClient saasPlanClient,
            AiUsageMetricsRepository metricsRepository
    ) {
        this.saasPlanClient = saasPlanClient;
        this.metricsRepository = metricsRepository;
    }

    public void requireQuotaAvailable(UUID studyServerId) {
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
}
