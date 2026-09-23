package com.chanter.community.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record CreateCourseRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 2000) String description,
        @Size(max = 120) String cohortName,
        @Pattern(regexp = "OPEN|INVITE_ONLY|OPENING_SOON|CLOSED") String enrollmentPolicy
) {
}
