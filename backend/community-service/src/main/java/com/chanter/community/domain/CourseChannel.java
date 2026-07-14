package com.chanter.community.domain;

import java.util.UUID;

public record CourseChannel(
        UUID id,
        UUID courseId,
        UUID cohortId,
        String name,
        ChannelKind kind,
        int position
) {
}
