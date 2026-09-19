package com.chanter.common.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public final class JwtTokenService {

    private final byte[] secret;
    private final long accessTokenTtlSeconds;

    public JwtTokenService(String secret, long accessTokenTtlSeconds) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be blank");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 256 bits");
        }
        // Reject the historical in-git example value (SEC-04). Length-only checks are not enough.
        if ("chanter-local-dev-jwt-secret-32bytes!!".equals(secret)) {
            throw new IllegalArgumentException(
                    "JWT secret rejects known default value; set CHANTER_JWT_SECRET via make product-env");
        }
        if (accessTokenTtlSeconds <= 0) {
            throw new IllegalArgumentException("JWT access token TTL must be positive");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public String createAccessToken(UUID userId) {
        return createAccessToken(userId, null);
    }

    public String createAccessToken(UUID userId, UUID sessionId) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(accessTokenTtlSeconds)))
                .claim("sid", sessionId == null ? null : sessionId.toString())
                .build();
        try {
            SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJwt.sign(new MACSigner(secret));
            return signedJwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Failed to sign access token", exception);
        }
    }

    public UUID parseUserId(String authorizationHeader) {
        return UUID.fromString(verifiedClaims(authorizationHeader).getSubject());
    }

    /** Native capability issuance requires a durable session; legacy web tokens remain valid elsewhere. */
    public AccessSession parseAccessSession(String authorizationHeader) {
        JWTClaimsSet claims = verifiedClaims(authorizationHeader);
        try {
            return new AccessSession(UUID.fromString(claims.getSubject()), UUID.fromString(claims.getStringClaim("sid")),
                    claims.getExpirationTime().toInstant());
        } catch (ParseException | IllegalArgumentException | NullPointerException missingSession) {
            throw new InvalidJwtException("Access token has no valid session");
        }
    }

    private JWTClaimsSet verifiedClaims(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(AuthHeaders.BEARER_PREFIX)) {
            throw new InvalidJwtException("Missing or invalid Authorization header");
        }
        String token = authorizationHeader.substring(AuthHeaders.BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new InvalidJwtException("Missing bearer token");
        }
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(signedJwt.getHeader().getAlgorithm()) || !signedJwt.verify(new MACVerifier(secret))) {
                throw new InvalidJwtException("Invalid token signature");
            }
            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.toInstant().isAfter(Instant.now())) {
                throw new InvalidJwtException("Token expired");
            }
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new InvalidJwtException("Token missing subject");
            }
            UUID.fromString(subject);
            return claims;
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw new InvalidJwtException("Invalid access token", exception);
        }
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public record AccessSession(UUID userId, UUID sessionId, Instant expiresAt) {}
}
