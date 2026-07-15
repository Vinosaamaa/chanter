package com.chanter.community.domain;

import java.util.List;

public record CourseOverviewSummary(
        Integer progress,
        String progressUnavailableReason,
        List<CourseOverviewItem> thisWeek,
        List<CourseOverviewItem> recentActivity,
        List<CourseOverviewItem> upNext,
        List<String> partialFailures
) {
}
