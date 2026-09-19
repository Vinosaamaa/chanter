package com.chanter.auth.api;

import com.chanter.auth.application.TurnstileVerification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class HumanVerificationController {
    private final TurnstileVerification verification;
    public HumanVerificationController(TurnstileVerification verification) { this.verification = verification; }

    @GetMapping("/api/v1/auth/verification-options")
    public TurnstileVerification.Options options() { return verification.options(); }
}
