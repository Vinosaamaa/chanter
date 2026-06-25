package com.chanter.community.api;

import com.chanter.community.application.SaasPlanService;
import com.chanter.community.domain.StudyServerSaasPlan;
import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/study-servers/{studyServerId}")
public class SaasPlanController {

    private final SaasPlanService saasPlanService;

    public SaasPlanController(SaasPlanService saasPlanService) {
        this.saasPlanService = saasPlanService;
    }

    @GetMapping("/saas-plan")
    public SaasPlanResponse getSaasPlan(@PathVariable UUID studyServerId) {
        StudyServerSaasPlan plan = saasPlanService.findPlan(studyServerId);
        return SaasPlanResponse.from(plan);
    }

    @PatchMapping("/saas-plan")
    public SaasPlanResponse updateSaasPlan(
            @PathVariable UUID studyServerId,
            @Valid @RequestBody UpdateSaasPlanRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID ownerUserId
    ) {
        StudyServerSaasPlan plan = saasPlanService.updatePlan(
                studyServerId,
                ownerUserId,
                request.planTier()
        );
        return SaasPlanResponse.from(plan);
    }
}
