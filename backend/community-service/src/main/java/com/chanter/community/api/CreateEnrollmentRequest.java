package com.chanter.community.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateEnrollmentRequest(
        @Email @Size(max = 320) String email,
        UUID learnerUserId
) {

    public CreateEnrollmentRequest {
        email = email == null ? null : email.trim();
    }

    @JsonIgnore
    @AssertTrue(message = "Provide exactly one learner identity")
    public boolean isIdentitySelectionValid() {
        boolean hasEmail = email != null && !email.isBlank();
        return hasEmail != (learnerUserId != null);
    }
}
