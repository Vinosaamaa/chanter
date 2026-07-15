package com.chanter.community.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.community.application.HomeSummaryService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/me")
public class HomeSummaryController {

    private final HomeSummaryService homeSummaryService;

    public HomeSummaryController(HomeSummaryService homeSummaryService) {
        this.homeSummaryService = homeSummaryService;
    }

    @GetMapping("/home-summary")
    public HomeSummaryResponse getHomeSummary(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return HomeSummaryResponse.from(homeSummaryService.buildHomeSummary(userId));
    }
}
