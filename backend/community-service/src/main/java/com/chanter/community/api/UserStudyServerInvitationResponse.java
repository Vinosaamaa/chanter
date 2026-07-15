package com.chanter.community.api;

import com.chanter.community.domain.StudyServerInvitation;
import java.util.UUID;

public record UserStudyServerInvitationResponse(
        UUID id,
        UUID studyServerId,
        String studyServerName,
        String email
) {

    public static UserStudyServerInvitationResponse from(
            StudyServerInvitation invitation,
            String studyServerName
    ) {
        return new UserStudyServerInvitationResponse(
                invitation.id(),
                invitation.studyServerId(),
                studyServerName,
                invitation.email()
        );
    }
}
