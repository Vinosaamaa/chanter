package com.chanter.community.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Course(
        UUID id,
        UUID studyServerId,
        String title,
        InstructorRole instructorRole,
        Cohort cohort,
        List<CourseChannel> channels,
        Instant createdAt
) {
}
