package com.chanter.community.domain;

import java.util.UUID;

public record StudyServerNavigationCohort(
        UUID id,
        String name,
        CohortCapabilities capabilities
) {
}
