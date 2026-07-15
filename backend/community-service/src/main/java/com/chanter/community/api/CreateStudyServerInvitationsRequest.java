package com.chanter.community.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateStudyServerInvitationsRequest(
        @NotEmpty List<@NotNull @NotBlank @Email String> inviteEmails
) {
}
