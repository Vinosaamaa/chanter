package com.chanter.auth.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.auth.application.AuthSessionService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/auth")
public class AuthController {

    private final AuthSessionService authSessionService;

    public AuthController(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "auth-service"
        );
    }

    @PostMapping("/register")
    public ResponseEntity<AuthSessionResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthSessionResponse.from(authSessionService.register(
                        request.email(),
                        request.password(),
                        request.displayName()
                )));
    }

    @PostMapping("/login")
    public AuthSessionResponse login(@Valid @RequestBody LoginRequest request) {
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

    @GetMapping("/me")
    public AuthUserResponse me(@RequestHeader(AuthHeaders.AUTHORIZATION) String authorizationHeader) {
        UUID userId = authSessionService.requireUserIdFromAccessToken(authorizationHeader);
        return AuthUserResponse.from(authSessionService.requireUser(userId));
    }
}
