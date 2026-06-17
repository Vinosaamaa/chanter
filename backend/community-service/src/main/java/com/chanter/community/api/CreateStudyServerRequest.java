package com.chanter.community.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateStudyServerRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull UUID ownerUserId
) {
}
