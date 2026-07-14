package com.chanter.community.api;

import com.chanter.community.domain.ChannelKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCourseChannelRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull ChannelKind kind
) {
}
