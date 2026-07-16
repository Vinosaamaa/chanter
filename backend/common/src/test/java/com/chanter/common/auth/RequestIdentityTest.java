package com.chanter.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestIdentityTest {

    private static final String JWT_SECRET = "chanter-test-jwt-secret-32bytes-min!!";
    private static final byte[] INTERNAL_TOKEN = "test-internal-service-token".getBytes(StandardCharsets.UTF_8);

    private JwtTokenService jwtTokenService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(JWT_SECRET, 900);
        userId = UUID.randomUUID();
    }

    @Test
    void acceptsValidJwtWithoutUserIdHeader() {
        String token = jwtTokenService.createAccessToken(userId);
        UUID resolved = RequestIdentity.requireUserId(
                AuthHeaders.BEARER_PREFIX + token,
                null,
                null,
                INTERNAL_TOKEN,
                jwtTokenService
        );
        assertThat(resolved).isEqualTo(userId);
    }

    @Test
    void acceptsValidJwtWithMatchingUserIdHeader() {
        String token = jwtTokenService.createAccessToken(userId);
        UUID resolved = RequestIdentity.requireUserId(
                AuthHeaders.BEARER_PREFIX + token,
                userId.toString(),
                null,
                INTERNAL_TOKEN,
                jwtTokenService
        );
        assertThat(resolved).isEqualTo(userId);
    }

    @Test
    void rejectsSpoofedUserIdHeaderWithValidJwt() {
        String token = jwtTokenService.createAccessToken(userId);
        assertThatThrownBy(() -> RequestIdentity.requireUserId(
                AuthHeaders.BEARER_PREFIX + token,
                UUID.randomUUID().toString(),
                null,
                INTERNAL_TOKEN,
                jwtTokenService
        )).isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void rejectsUserIdHeaderAlone() {
        assertThatThrownBy(() -> RequestIdentity.requireUserId(
                null,
                userId.toString(),
                null,
                INTERNAL_TOKEN,
                jwtTokenService
        )).isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void acceptsInternalTokenWithUserIdHeader() {
        UUID resolved = RequestIdentity.requireUserId(
                null,
                userId.toString(),
                "test-internal-service-token",
                INTERNAL_TOKEN,
                jwtTokenService
        );
        assertThat(resolved).isEqualTo(userId);
    }

    @Test
    void rejectsWrongInternalTokenEvenWithUserIdHeader() {
        assertThatThrownBy(() -> RequestIdentity.requireUserId(
                null,
                userId.toString(),
                "wrong-token",
                INTERNAL_TOKEN,
                jwtTokenService
        )).isInstanceOf(InvalidJwtException.class);
    }
}
