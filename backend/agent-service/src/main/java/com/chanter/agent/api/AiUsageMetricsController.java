package com.chanter.agent.api;

import com.chanter.agent.application.AiUsageMetricsService;
import com.chanter.agent.domain.AiUsageMetrics;
import com.chanter.common.ServiceInfo;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/study-servers/{studyServerId}")
public class AiUsageMetricsController {

    private final AiUsageMetricsService aiUsageMetricsService;

    public AiUsageMetricsController(AiUsageMetricsService aiUsageMetricsService) {
        this.aiUsageMetricsService = aiUsageMetricsService;
    }

    @GetMapping("/ai-usage-metrics")
    public AiUsageMetricsResponse getAiUsageMetrics(
            @PathVariable UUID studyServerId,
            @RequestParam UUID viewerUserId
    ) {
        AiUsageMetrics metrics = aiUsageMetricsService.findMetrics(studyServerId, viewerUserId);
        return AiUsageMetricsResponse.from(metrics);
    }
}
