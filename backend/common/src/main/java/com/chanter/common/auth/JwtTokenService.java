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
        if (accessTokenTtlSeconds <= 0) {
            throw new IllegalArgumentException("JWT access token TTL must be positive");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public String createAccessToken(UUID userId) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(accessTokenTtlSeconds)))
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
        if (authorizationHeader == null || !authorizationHeader.startsWith(AuthHeaders.BEARER_PREFIX)) {
            throw new InvalidJwtException("Missing or invalid Authorization header");
        }
        String token = authorizationHeader.substring(AuthHeaders.BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new InvalidJwtException("Missing bearer token");
        }
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            if (!signedJwt.verify(new MACVerifier(secret))) {
                throw new InvalidJwtException("Invalid token signature");
            }
            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.before(new Date())) {
                throw new InvalidJwtException("Token expired");
            }
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new InvalidJwtException("Token missing subject");
            }
            return UUID.fromString(subject);
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw new InvalidJwtException("Invalid access token", exception);
        }
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }
}
