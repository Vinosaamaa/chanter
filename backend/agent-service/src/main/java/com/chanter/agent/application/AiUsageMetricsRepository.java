package com.chanter.agent.application;

import java.util.UUID;

public interface AiUsageMetricsRepository {

    AiUsageMetrics findMetrics(UUID studyServerId);
}
