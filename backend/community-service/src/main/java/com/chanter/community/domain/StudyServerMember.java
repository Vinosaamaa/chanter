package com.chanter.community.domain;

import java.util.UUID;

public record StudyServerMember(
        UUID userId,
        String role
) {
}
