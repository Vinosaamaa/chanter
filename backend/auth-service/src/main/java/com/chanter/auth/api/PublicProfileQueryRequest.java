package com.chanter.auth.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record PublicProfileQueryRequest(
        @NotEmpty @Size(max = 100) List<@NotNull UUID> userIds
) {
}
