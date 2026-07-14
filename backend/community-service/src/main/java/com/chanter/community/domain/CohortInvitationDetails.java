package com.chanter.community.domain;

public record CohortInvitationDetails(
        CohortInvitation invitation,
        AuthUserProfile profile
) {
}
