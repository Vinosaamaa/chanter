package com.chanter.community.application;

import com.chanter.community.domain.SaasPlanTier;
import com.chanter.community.domain.StudyServerSaasPlan;
import java.util.Optional;
import java.util.UUID;

public interface SaasPlanRepository {

    Optional<StudyServerSaasPlan> findByStudyServerId(UUID studyServerId);

    boolean updatePlanTierIfOwner(UUID studyServerId, UUID ownerUserId, SaasPlanTier planTier);
}
