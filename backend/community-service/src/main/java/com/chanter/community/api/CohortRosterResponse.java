package com.chanter.community.api;

import com.chanter.community.domain.CohortRoster;
import java.util.List;
import java.util.UUID;

public record CohortRosterResponse(
        UUID cohortId,
        CohortRosterMemberResponse instructor,
        List<CohortRosterMemberResponse> teachingAssistants,
        List<CohortRosterMemberResponse> learners,
        int learnerCount,
        int teachingAssistantCount,
        int pendingCount,
        int limit,
        int offset
) {

    static CohortRosterResponse from(CohortRoster roster) {
        return new CohortRosterResponse(
                roster.cohortId(),
                CohortRosterMemberResponse.from(roster.instructor()),
                roster.teachingAssistants().stream().map(CohortRosterMemberResponse::from).toList(),
                roster.learners().stream().map(CohortRosterMemberResponse::from).toList(),
                roster.learnerCount(),
                roster.teachingAssistantCount(),
                roster.pendingCount(),
                roster.limit(),
                roster.offset()
        );
    }
}
