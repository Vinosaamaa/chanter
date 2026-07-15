package com.chanter.community.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record CourseLifecycle(
        UUID id,
        UUID studyServerId,
        String title,
        String description,
        UUID instructorUserId,
        boolean published,
        boolean archived,
        Optional<Cohort> cohort,
        List<CourseChannel> channels,
        Instant createdAt
) {
}
