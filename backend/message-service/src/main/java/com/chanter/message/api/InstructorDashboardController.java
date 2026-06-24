package com.chanter.message.api;

import com.chanter.common.ServiceInfo;
import com.chanter.message.application.InstructorDashboardMetricsService;
import com.chanter.message.application.InstructorDashboardMessageMetrics;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/instructor-dashboard")
public class InstructorDashboardController {

    private final InstructorDashboardMetricsService instructorDashboardMetricsService;

    public InstructorDashboardController(InstructorDashboardMetricsService instructorDashboardMetricsService) {
        this.instructorDashboardMetricsService = instructorDashboardMetricsService;
    }

    @PostMapping("/message-metrics")
    public InstructorDashboardMessageMetricsResponse getMessageMetrics(
            @Valid @RequestBody InstructorDashboardMessageMetricsRequest request
    ) {
        InstructorDashboardMessageMetrics metrics = instructorDashboardMetricsService.buildMetrics(request);
        return InstructorDashboardMessageMetricsResponse.from(metrics);
    }
}
