package com.chanter.community.domain;

import java.util.List;
import java.util.UUID;

public record CohortRoster(
        UUID cohortId,
        CohortRosterMember instructor,
        List<CohortRosterMember> teachingAssistants,
        List<CohortRosterMember> learners,
        int learnerCount,
        int teachingAssistantCount,
        int pendingCount,
        int limit,
        int offset
) {
}
