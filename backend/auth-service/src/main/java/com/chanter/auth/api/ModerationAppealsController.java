package com.chanter.auth.api;

import com.chanter.auth.application.AuthRateLimiter;
import com.chanter.auth.moderation.ModerationAppeals;
import com.chanter.auth.moderation.ModerationRequestContext;
import com.chanter.auth.moderation.OperatorAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/moderation-appeals")
public class ModerationAppealsController {
    private final ModerationAppeals appeals;
    private final AuthRateLimiter limiter;

    public ModerationAppealsController(ModerationAppeals appeals, AuthRateLimiter limiter) {
        this.appeals = appeals;
        this.limiter = limiter;
    }

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, String> request(@Valid @RequestBody LinkRequest body, HttpServletRequest request) {
        limiter.check("appeal-link-ip:" + ClientIpResolver.resolve(request));
        limiter.check("appeal-link-account:" + OperatorAccess.hash(body.email().strip().toLowerCase(Locale.ROOT)));
        appeals.request(body.email(), body.restrictionId());
        return Map.of("message", "If this restriction belongs to a verified account, an appeal link will be sent.");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, String> submit(@Valid @RequestBody AppealRequest body, HttpServletRequest request,
            @RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        limiter.check("appeal-submit-ip:" + ClientIpResolver.resolve(request));
        limiter.check("appeal-submit-token:" + OperatorAccess.hash(body.token()));
        appeals.submit(body.token(), body.body(), correlation);
        return Map.of("message", "Your appeal has been recorded for review.");
    }

    record LinkRequest(@NotBlank @Email @Size(max = 320) String email, @NotNull UUID restrictionId) { }
    record AppealRequest(@NotBlank @Size(max = 128) String token, @NotBlank @Size(max = 4000) String body) { }
}
