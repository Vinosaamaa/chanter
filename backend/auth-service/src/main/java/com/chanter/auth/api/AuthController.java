package com.chanter.auth.api;

import com.chanter.auth.application.AuthRateLimiter;
import com.chanter.auth.application.AuthSessionService;
import com.chanter.auth.application.AuthSessionService.RegisterResult;
import com.chanter.auth.application.OAuthAuthService;
import com.chanter.auth.application.ProductionAuthService;
import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/auth")
public class AuthController {

    private final AuthSessionService authSessionService;
    private final ProductionAuthService productionAuthService;
    private final OAuthAuthService oauthAuthService;
    private final AuthRateLimiter authRateLimiter;

    public AuthController(
            AuthSessionService authSessionService,
            ProductionAuthService productionAuthService,
            OAuthAuthService oauthAuthService,
            AuthRateLimiter authRateLimiter
    ) {
        this.authSessionService = authSessionService;
        this.productionAuthService = productionAuthService;
        this.oauthAuthService = oauthAuthService;
        this.authRateLimiter = authRateLimiter;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "auth-service"
        );
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.check(rateKey(servletRequest, "register"));
        RegisterResult result = authSessionService.registerWithStatus(
                request.email(),
                request.password(),
                request.displayName()
        );
        if (result.verificationRequired()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "verificationRequired", true,
                            "message", result.message()
                    ));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthSessionResponse.from(result.session()));
    }

    @PostMapping("/login")
    public AuthSessionResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.check(rateKey(servletRequest, "login", request.email()));
        return AuthSessionResponse.from(authSessionService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public AuthSessionResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return AuthSessionResponse.from(authSessionService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authSessionService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.check(rateKey(servletRequest, "forgot-password"));
        productionAuthService.requestPasswordReset(request.email());
        return ResponseEntity.ok(Map.of(
                "message", "If that email is registered, a reset link has been sent."
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.check(rateKey(servletRequest, "reset-password"));
        productionAuthService.resetPassword(request.token(), request.password());
        return ResponseEntity.ok(Map.of("message", "Password updated. You can sign in with your new password."));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.check(rateKey(servletRequest, "verify-email"));
        productionAuthService.verifyEmail(request.token());
        return ResponseEntity.ok(Map.of("message", "Email verified. You can sign in."));
    }

    @GetMapping("/oauth/providers")
    public OAuthProvidersResponse listOauthProviders() {
        List<OAuthAuthService.ProviderInfo> providers = oauthAuthService.listProviders();
        return new OAuthProvidersResponse(providers.stream()
                .map(provider -> new OAuthProviderResponse(provider.id(), provider.label(), provider.authorizationUrl()))
                .toList());
    }

    @GetMapping("/oauth/{provider}/start")
    public ResponseEntity<Void> startOauth(@PathVariable String provider) {
        String url = oauthAuthService.authorizationUrl(provider);
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", url).build();
    }

    @PostMapping("/oauth/google/callback")
    public AuthSessionResponse googleCallback(
            @Valid @RequestBody OAuthCodeRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.check(rateKey(servletRequest, "oauth-google"));
        return AuthSessionResponse.from(oauthAuthService.completeGoogleLogin(request.code()));
    }

    @GetMapping("/me")
    public AuthUserResponse me(
            @RequestHeader(value = AuthHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        UUID userId = authSessionService.requireUserIdFromAccessToken(authorizationHeader);
        return AuthUserResponse.from(authSessionService.requireUser(userId));
    }

    @PostMapping("/profiles/query")
    public PublicProfileListResponse findPublicProfiles(
            @RequestHeader(value = AuthHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @Valid @RequestBody PublicProfileQueryRequest request
    ) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        authSessionService.requireUserIdFromAccessToken(authorizationHeader);
        return PublicProfileListResponse.from(
                authSessionService.findPublicProfiles(request.userIds())
        );
    }

    private static String rateKey(HttpServletRequest request, String action) {
        return action + ":" + ClientIpResolver.resolve(request);
    }

    private static String rateKey(HttpServletRequest request, String action, String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        return action + ":" + normalizedEmail + ":" + ClientIpResolver.resolve(request);
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 200) String password
    ) {
    }

    public record VerifyEmailRequest(@NotBlank String token) {
    }

    public record OAuthCodeRequest(@NotBlank String code) {
    }

    public record OAuthProvidersResponse(List<OAuthProviderResponse> providers) {
    }

    public record OAuthProviderResponse(String id, String label, String authorizationUrl) {
    }
}
