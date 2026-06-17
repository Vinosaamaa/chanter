package com.chanter.community.domain;

import java.util.UUID;

public record CourseChannel(
        UUID id,
        UUID courseId,
        String name,
        ChannelKind kind,
        int position
) {
}
