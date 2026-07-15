package com.chanter.community.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.community.application.CourseOverviewSummaryService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/courses")
public class CourseOverviewSummaryController {

    private final CourseOverviewSummaryService courseOverviewSummaryService;

    public CourseOverviewSummaryController(CourseOverviewSummaryService courseOverviewSummaryService) {
        this.courseOverviewSummaryService = courseOverviewSummaryService;
    }

    @GetMapping("/{courseId}/overview-summary")
    public CourseOverviewSummaryResponse getOverviewSummary(
            @PathVariable UUID courseId,
            @RequestParam(required = false) UUID cohortId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return CourseOverviewSummaryResponse.from(
                courseOverviewSummaryService.buildOverviewSummary(courseId, cohortId, userId)
        );
    }
}
