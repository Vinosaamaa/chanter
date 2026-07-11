package com.chanter.community.api;

import java.util.UUID;

public record CohortInviteResponse(UUID cohortId, UUID inviteCode) {
}
