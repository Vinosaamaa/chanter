package com.chanter.community.domain;

import java.util.List;
import java.util.UUID;

public record GrantCandidateCourse(
        UUID id,
        String title,
        List<GrantCandidateCohort> cohorts,
        List<CourseChannel> channels
) {
}
