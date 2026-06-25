package com.chanter.community.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCourseRequest(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 120) String cohortName
) {
}
