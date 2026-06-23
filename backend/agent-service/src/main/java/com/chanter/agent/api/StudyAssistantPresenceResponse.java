package com.chanter.agent.api;

import com.chanter.agent.application.StudyAssistantService.Presence;
import com.chanter.agent.domain.GrantType;
import com.chanter.agent.domain.StudyAssistantGrant;
import java.util.List;
import java.util.UUID;

public record StudyAssistantPresenceResponse(
        UUID studyServerId,
        boolean installed,
        List<GrantResponse> grants
) {

    static StudyAssistantPresenceResponse from(Presence presence) {
        return new StudyAssistantPresenceResponse(
                presence.studyServerId(),
                presence.installed(),
                presence.grants().stream()
                        .map(GrantResponse::from)
                        .toList()
        );
    }

    public record GrantResponse(
            GrantType grantType,
            UUID grantTargetId
    ) {
        static GrantResponse from(StudyAssistantGrant grant) {
            return new GrantResponse(grant.grantType(), grant.grantTargetId());
        }
    }
}
