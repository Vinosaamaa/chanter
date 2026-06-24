package com.chanter.agent.application;

import com.chanter.agent.domain.AiUsageMetrics;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiUsageMetricsService {

    private final StudyAssistantGrantCandidatesClient grantCandidatesClient;
    private final AiUsageMetricsRepository metricsRepository;

    public AiUsageMetricsService(
            StudyAssistantGrantCandidatesClient grantCandidatesClient,
            AiUsageMetricsRepository metricsRepository
    ) {
        this.grantCandidatesClient = grantCandidatesClient;
        this.metricsRepository = metricsRepository;
    }

    public AiUsageMetrics findMetrics(UUID studyServerId, UUID viewerUserId) {
        grantCandidatesClient.requireGrantCandidates(studyServerId, viewerUserId);
        return metricsRepository.findMetrics(studyServerId);
    }
}
