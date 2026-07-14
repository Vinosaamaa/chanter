package com.chanter.community.api;

import com.chanter.community.domain.CohortRosterMember;
import java.time.Instant;
import java.util.UUID;

public record CohortRosterMemberResponse(
        UUID userId,
        UUID invitationId,
        String displayName,
        String email,
        String role,
        String status,
        UUID assignedTeachingAssistantUserId,
        Instant enrolledAt
) {

    static CohortRosterMemberResponse from(CohortRosterMember member) {
        return new CohortRosterMemberResponse(
                member.userId(),
                member.invitationId(),
                member.displayName(),
                member.email(),
                member.role(),
                member.status(),
                member.assignedTeachingAssistantUserId(),
                member.enrolledAt()
        );
    }
}
