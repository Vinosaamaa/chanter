package com.chanter.analytics.api;

import com.chanter.analytics.application.InstructorDashboardService;
import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthHeaders;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/study-servers/{studyServerId}")
public class InstructorDashboardController {

    private final InstructorDashboardService instructorDashboardService;

    public InstructorDashboardController(InstructorDashboardService instructorDashboardService) {
        this.instructorDashboardService = instructorDashboardService;
    }

    @GetMapping("/instructor-dashboard")
    public InstructorDashboardResponse getInstructorDashboard(
            @PathVariable UUID studyServerId,
            @RequestHeader(AuthHeaders.USER_ID) UUID viewerUserId
    ) {
        return instructorDashboardService.buildDashboard(studyServerId, viewerUserId);
    }
}
