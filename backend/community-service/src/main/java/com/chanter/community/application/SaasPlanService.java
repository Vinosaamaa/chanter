package com.chanter.community.application;

import com.chanter.community.domain.SaasPlanTier;
import com.chanter.community.config.FreeBetaProperties;
import com.chanter.community.domain.StudyServerSaasPlan;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SaasPlanService {

    private final SaasPlanRepository saasPlanRepository;
    private final FreeBetaProperties beta;

    public SaasPlanService(SaasPlanRepository saasPlanRepository, FreeBetaProperties beta) {
        this.saasPlanRepository = saasPlanRepository;
        this.beta = beta;
    }

    public StudyServerSaasPlan findPlan(UUID studyServerId) {
        return saasPlanRepository.findByStudyServerId(studyServerId)
                .map(plan -> new StudyServerSaasPlan(plan.studyServerId(), SaasPlanTier.FREE_BETA,
                        beta.assistantRunLimit()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));
    }

    public StudyServerSaasPlan updatePlan(UUID studyServerId, UUID ownerUserId, SaasPlanTier planTier) {
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Free-beta limits are managed by the platform operator"
        );
    }
}
