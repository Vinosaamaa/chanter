package com.chanter.agent.api;

import com.chanter.agent.domain.ConfirmedGrant;
import com.chanter.agent.domain.GrantType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record InstallStudyAssistantRequest(
        @NotEmpty List<@Valid ConfirmedGrantRequest> grants
) {

    public List<ConfirmedGrant> toConfirmedGrants() {
        return grants.stream()
                .map(ConfirmedGrantRequest::toConfirmedGrant)
                .toList();
    }

    public record ConfirmedGrantRequest(
            @NotNull GrantType grantType,
            @NotNull UUID grantTargetId
    ) {
        ConfirmedGrant toConfirmedGrant() {
            return new ConfirmedGrant(grantType, grantTargetId);
        }
    }
}
