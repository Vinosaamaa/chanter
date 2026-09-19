package com.chanter.common.events;

import com.chanter.common.auth.InternalServiceTokens;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class InternalEventAccess {
    private final byte[] token;
    public InternalEventAccess(String token) { this.token = InternalServiceTokens.requireBytes(token); }
    public void require(String presented) {
        if (!MessageDigest.isEqual(token, presented == null ? new byte[0] : presented.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal service authentication required");
        }
    }
}
