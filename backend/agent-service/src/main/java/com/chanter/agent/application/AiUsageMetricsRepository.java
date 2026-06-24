package com.chanter.agent.application;

import com.chanter.agent.domain.AiInvocationCounts;
import java.util.UUID;

public interface AiUsageMetricsRepository {

    AiInvocationCounts findInvocationCounts(UUID studyServerId);
}
