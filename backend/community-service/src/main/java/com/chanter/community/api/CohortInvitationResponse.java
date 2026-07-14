package com.chanter.community.api;

import com.chanter.community.domain.AuthUserProfile;
import com.chanter.community.domain.CohortInvitation;
import com.chanter.community.domain.CohortInvitationDetails;
import java.time.Instant;
import java.util.UUID;

public record CohortInvitationResponse(
        UUID id,
        UUID userId,
        String displayName,
        String email,
        String status,
        Instant createdAt
) {

    static CohortInvitationResponse from(CohortInvitation invitation, AuthUserProfile profile) {
        return new CohortInvitationResponse(
                invitation.id(),
                invitation.invitedUserId(),
                profile.displayName(),
                invitation.email(),
                invitation.status().name(),
                invitation.createdAt()
        );
    }

    static CohortInvitationResponse from(CohortInvitationDetails details) {
        return from(details.invitation(), details.profile());
    }
}
