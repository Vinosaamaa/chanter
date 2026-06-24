package com.chanter.community.application;

import com.chanter.community.domain.SaasPlanTier;
import com.chanter.community.domain.StudyServerSaasPlan;
import java.util.Optional;
import java.util.UUID;

public interface SaasPlanRepository {

    Optional<StudyServerSaasPlan> findByStudyServerId(UUID studyServerId);

    void updatePlanTier(UUID studyServerId, SaasPlanTier planTier);

    boolean isStudyServerOwner(UUID studyServerId, UUID userId);
}
