package com.chanter.community.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCourseChannelRequest(
        @NotBlank @Size(max = 80) String name
) {
}
