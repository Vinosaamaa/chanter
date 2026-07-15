package com.chanter.community.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCohortRequest(
        @NotBlank @Size(max = 120) String name
) {
}
