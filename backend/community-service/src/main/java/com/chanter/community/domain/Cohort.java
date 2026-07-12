package com.chanter.community.domain;

import java.util.UUID;

public record Cohort(
        UUID id,
        UUID courseId,
        String name,
        UUID inviteCode
) {
}
