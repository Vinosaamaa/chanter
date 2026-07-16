package com.chanter.common.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Resolves the acting user for public service APIs (SEC-01).
 *
 * <p>Accepts either a validated Bearer JWT (gateway / browser path), or a matching
 * internal service token plus {@code X-User-Id} (trusted service-to-service hops).
 * {@code X-User-Id} alone is never enough.
 */
public final class RequestIdentity {

    private RequestIdentity() {
    }

    public static UUID requireUserId(
            String authorizationHeader,
            String userIdHeader,
            String presentedInternalToken,
            byte[] configuredInternalToken,
            JwtTokenService jwtTokenService
    ) {
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            UUID userId = jwtTokenService.parseUserId(authorizationHeader);
            if (userIdHeader != null && !userIdHeader.isBlank()) {
                UUID headerUserId;
                try {
                    headerUserId = UUID.fromString(userIdHeader.trim());
                } catch (IllegalArgumentException exception) {
                    throw new InvalidJwtException("Invalid authenticated user id", exception);
                }
                if (!userId.equals(headerUserId)) {
                    throw new InvalidJwtException("X-User-Id does not match access token subject");
                }
            }
            return userId;
        }

        if (isInternalService(presentedInternalToken, configuredInternalToken)) {
            if (userIdHeader == null || userIdHeader.isBlank()) {
                throw new InvalidJwtException("Authentication required");
            }
            try {
                return UUID.fromString(userIdHeader.trim());
            } catch (IllegalArgumentException exception) {
                throw new InvalidJwtException("Invalid authenticated user id", exception);
            }
        }

        throw new InvalidJwtException("Authentication required");
    }

    private static boolean isInternalService(String presentedToken, byte[] configuredToken) {
        if (configuredToken == null || configuredToken.length == 0) {
            return false;
        }
        byte[] presented = presentedToken == null
                ? new byte[0]
                : presentedToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(configuredToken, presented);
    }
}
