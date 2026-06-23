package com.chanter.agent.domain;

import java.util.UUID;

public record StudyAssistantGrant(
        UUID id,
        UUID installId,
        GrantType grantType,
        UUID grantTargetId
) {
}
