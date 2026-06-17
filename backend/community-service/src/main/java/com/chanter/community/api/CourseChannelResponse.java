package com.chanter.community.api;

import com.chanter.community.domain.CourseChannel;
import java.util.UUID;

public record CourseChannelResponse(UUID id, String name, String kind) {

    static CourseChannelResponse from(CourseChannel channel) {
        return new CourseChannelResponse(channel.id(), channel.name(), channel.kind().name());
    }
}
