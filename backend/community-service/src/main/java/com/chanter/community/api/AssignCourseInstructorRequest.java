package com.chanter.community.api;

import jakarta.validation.constraints.Email;
import java.util.UUID;

public record AssignCourseInstructorRequest(
        UUID instructorUserId,
        @Email String instructorEmail
) {
}
