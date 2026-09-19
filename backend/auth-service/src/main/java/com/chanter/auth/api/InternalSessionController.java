package com.chanter.auth.api;

import com.chanter.auth.application.AuthSessionService;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.auth.JwtTokenService.AccessSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Internal read-only proof of live browser-session authority; no refresh credential leaves auth. */
@RestController
public class InternalSessionController {
    private final AuthSessionService sessions;
    private final byte[] serviceToken;
    public InternalSessionController(AuthSessionService sessions, @Value("${chanter.internal-service-token}") String token) {
        this.sessions = sessions;
        this.serviceToken = InternalServiceTokens.requireBytes(token);
    }

    @PostMapping("/internal/v1/auth/session/introspect")
    public ResponseEntity<AccessSession> introspect(
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String token,
            @RequestHeader(value = AuthHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!MessageDigest.isEqual(serviceToken, token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal service authentication required");
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(sessions.requireActiveAccessSession(authorization));
    }
}
