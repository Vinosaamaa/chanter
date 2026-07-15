package com.chanter.community.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCommunityAnnouncementRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 8000) String body
) {
}
