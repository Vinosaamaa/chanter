package com.chanter.community.api;

import com.chanter.community.domain.Course;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record CourseResponse(
        UUID id,
        String title,
        InstructorRoleResponse instructorRole,
        CohortResponse cohort,
        List<CourseChannelResponse> channels
) {

    static CourseResponse from(Course course) {
        return new CourseResponse(
                course.id(),
                course.title(),
                new InstructorRoleResponse(
                        course.instructorRole().userId(),
                        course.instructorRole().role().name()
                ),
                new CohortResponse(course.cohort().id(), course.cohort().name()),
                course.channels().stream()
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
