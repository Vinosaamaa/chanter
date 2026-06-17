package com.chanter.community.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCourseRequest(
        @NotNull UUID ownerUserId,
        @NotBlank @Size(max = 160) String title,
        @NotNull UUID instructorUserId,
        @NotBlank @Size(max = 120) String cohortName
) {
}
