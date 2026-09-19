package com.chanter.auth.moderation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/verification")
public class OperatorVerificationController {
    private final OperatorVerification verification;
    public OperatorVerificationController(OperatorVerification verification) { this.verification = verification; }

    @PostMapping("/enrollment")
    OperatorVerification.Enrollment enroll(@RequestHeader("Authorization") String authorization,
            @Valid @RequestBody Password request,@RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        return verification.enroll(authorization, request.password(), correlation);
    }

    @PostMapping("/confirmation")
    OperatorVerification.Verified confirm(@RequestHeader("Authorization") String authorization,
            @Valid @RequestBody Challenge request,@RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        return verification.verify(authorization, request.password(), request.code(), true, correlation);
    }

    @PostMapping("/challenge")
    OperatorVerification.Verified challenge(@RequestHeader("Authorization") String authorization,
            @Valid @RequestBody Challenge request,@RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        return verification.verify(authorization, request.password(), request.code(), false, correlation);
    }

    record Password(@NotBlank @Size(max=128) String password) { }
    record Challenge(@NotBlank @Size(max=128) String password, @NotBlank @Pattern(regexp="[0-9]{6}") String code) { }
}
