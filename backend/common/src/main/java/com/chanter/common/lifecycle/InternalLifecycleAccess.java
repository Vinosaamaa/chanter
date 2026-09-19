package com.chanter.common.lifecycle;

import com.chanter.common.auth.InternalServiceTokens;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Private service authentication; browser authority remains in the auth-owned public controller. */
public final class InternalLifecycleAccess {
    private final byte[] token;
    public InternalLifecycleAccess(String token) { this.token = InternalServiceTokens.requireBytes(token); }
    public void require(String provided) {
        if (!MessageDigest.isEqual(token, provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal service authentication required");
    }
}
