package com.chanter.community.api;

import com.chanter.community.domain.CourseLifecycle;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CourseLifecycleResponse(
        UUID id,
        String title,
        String description,
        boolean published,
        boolean archived,
        InstructorRoleResponse instructorRole,
        CohortResponse cohort,
        List<CourseChannelResponse> channels
) {

    static CourseLifecycleResponse from(CourseLifecycle lifecycle) {
        return new CourseLifecycleResponse(
                lifecycle.id(),
                lifecycle.title(),
                lifecycle.description(),
                lifecycle.published(),
                lifecycle.archived(),
                new InstructorRoleResponse(
                        lifecycle.instructorUserId(),
                        "INSTRUCTOR"
                ),
                lifecycle.cohort()
                        .map(cohort -> new CohortResponse(cohort.id(), cohort.name()))
                        .orElse(null),
                lifecycle.channels().isEmpty()
                        ? null
                        : lifecycle.channels().stream()
                                .sorted(Comparator.comparingInt(channel -> channel.position()))
                                .map(CourseChannelResponse::from)
                                .toList()
        );
    }

    public record InstructorRoleResponse(UUID userId, String role) {
    }

    public record CohortResponse(UUID id, String name) {
    }
}
