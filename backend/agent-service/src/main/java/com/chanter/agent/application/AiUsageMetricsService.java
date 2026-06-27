package com.chanter.agent.application;

import com.chanter.agent.domain.AiInvocationCounts;
import com.chanter.agent.domain.AiUsageMetrics;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiUsageMetricsService {

    private final StudyAssistantGrantCandidatesClient grantCandidatesClient;
    private final StudyServerSaasPlanClient saasPlanClient;
    private final AiUsageMetricsRepository metricsRepository;

    public AiUsageMetricsService(
            StudyAssistantGrantCandidatesClient grantCandidatesClient,
            StudyServerSaasPlanClient saasPlanClient,
            AiUsageMetricsRepository metricsRepository
    ) {
        this.grantCandidatesClient = grantCandidatesClient;
        this.saasPlanClient = saasPlanClient;
        this.metricsRepository = metricsRepository;
    }

    public AiUsageMetrics findMetrics(UUID studyServerId, UUID viewerUserId) {
        grantCandidatesClient.requireGrantCandidates(studyServerId, viewerUserId);
        StudyServerSaasPlanClient.StudyServerSaasPlan plan = saasPlanClient.fetchPlan(studyServerId, viewerUserId);
        AiInvocationCounts usage = metricsRepository.findInvocationCounts(studyServerId);
        return new AiUsageMetrics(
                studyServerId,
                plan.planTier(),
                usage.totalInvocations(),
                plan.aiInvocationLimit(),
                usage.lowConfidenceHandoffs()
        );
    }
}
