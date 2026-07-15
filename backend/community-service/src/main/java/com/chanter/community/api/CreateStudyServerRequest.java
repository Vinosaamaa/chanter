package com.chanter.community.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateStudyServerRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        String serverType,
        List<@NotNull @NotBlank @Email String> inviteEmails
) {
}
