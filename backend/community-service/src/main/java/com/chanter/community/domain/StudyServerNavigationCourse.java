package com.chanter.community.domain;

import java.util.List;
import java.util.UUID;

public record StudyServerNavigationCourse(
        UUID id,
        String title,
        CourseCapabilities capabilities,
        List<StudyServerNavigationCohort> cohorts,
        List<CourseChannel> channels
) {
}
