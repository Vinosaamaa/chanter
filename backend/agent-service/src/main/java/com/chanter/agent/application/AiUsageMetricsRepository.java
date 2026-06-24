package com.chanter.agent.application;

import com.chanter.agent.domain.AiUsageMetrics;
import java.util.UUID;

public interface AiUsageMetricsRepository {

    AiUsageMetrics findMetrics(UUID studyServerId);
}
