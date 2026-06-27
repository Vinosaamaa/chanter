package com.chanter.community.api;

import com.chanter.community.application.InstructorDashboardService;
import com.chanter.community.domain.CommunityDashboardMetrics;
import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/study-servers/{studyServerId}")
public class InstructorDashboardController {

    private final InstructorDashboardService instructorDashboardService;

    public InstructorDashboardController(InstructorDashboardService instructorDashboardService) {
        this.instructorDashboardService = instructorDashboardService;
    }

    @GetMapping("/instructor-dashboard/community-metrics")
    public InstructorDashboardCommunityMetricsResponse getCommunityMetrics(
            @PathVariable UUID studyServerId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        CommunityDashboardMetrics metrics = instructorDashboardService.findCommunityMetrics(studyServerId, userId);
        return InstructorDashboardCommunityMetricsResponse.from(metrics);
    }
}
