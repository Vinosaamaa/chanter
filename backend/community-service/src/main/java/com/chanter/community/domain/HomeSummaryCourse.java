package com.chanter.community.domain;

import java.util.UUID;

public record HomeSummaryCourse(
        UUID courseId,
        UUID studyServerId,
        String title,
        UUID cohortId,
        String cohortName,
        String instructorDisplayName,
        Integer progress,
        String progressUnavailableReason,
        String href
) {
}
