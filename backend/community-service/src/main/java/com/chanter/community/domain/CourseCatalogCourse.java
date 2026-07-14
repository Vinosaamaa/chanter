package com.chanter.community.domain;

import java.util.List;
import java.util.UUID;

public record CourseCatalogCourse(
        UUID id,
        String title,
        UUID instructorUserId,
        List<CourseCatalogCohort> cohorts
) {
}
