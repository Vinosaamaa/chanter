package com.chanter.community.application;

import com.chanter.community.domain.SaasPlanTier;
import com.chanter.community.domain.StudyServerSaasPlan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SaasPlanService {

    private final SaasPlanRepository saasPlanRepository;

    public SaasPlanService(SaasPlanRepository saasPlanRepository) {
        this.saasPlanRepository = saasPlanRepository;
    }

    public StudyServerSaasPlan findPlan(UUID studyServerId) {
        return saasPlanRepository.findByStudyServerId(studyServerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));
    }

    public StudyServerSaasPlan updatePlan(UUID studyServerId, UUID ownerUserId, SaasPlanTier planTier) {
        if (saasPlanRepository.updatePlanTierIfOwner(studyServerId, ownerUserId, planTier)) {
            return findPlan(studyServerId);
        }

        if (saasPlanRepository.findByStudyServerId(studyServerId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found");
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only the Study Server Owner can change the SaaS Plan tier"
        );
    }
}
