package com.chanter.agent.domain;

import java.util.UUID;

public record ConfirmedGrant(
        GrantType grantType,
        UUID grantTargetId
) {
}
