package com.chanter.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.JwtTokenService;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class JwtAuthenticationGlobalFilterPublicAuthPathsTest {

    private static final String JWT_SECRET = "chanter-test-jwt-secret-32bytes-min!!";

    private JwtAuthenticationGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationGlobalFilter(new JwtTokenService(JWT_SECRET, 900));
    }

    static Stream<String> publicAuthPaths() {
        return Stream.of(
                "/api/v1/auth/health",
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout",
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/reset-password",
                "/api/v1/auth/verify-email",
                "/api/v1/auth/oauth/providers",
                "/api/v1/auth/oauth/google/start",
                "/api/v1/auth/oauth/google/callback"
        );
    }

    @ParameterizedTest
    @MethodSource("publicAuthPaths")
    void publicAuthPathsAreAllowListed(String path) {
        assertThat(JwtAuthenticationGlobalFilter.isPublicPath(path)).isTrue();
    }

    @Test
    void protectedAuthPathsStillRequireJwt() {
        assertThat(JwtAuthenticationGlobalFilter.isPublicPath("/api/v1/auth/me")).isFalse();
        assertThat(JwtAuthenticationGlobalFilter.isPublicPath("/api/v1/auth/profiles/query")).isFalse();
    }

    @ParameterizedTest
    @MethodSource("publicAuthPaths")
    void unauthenticatedPublicAuthPathsDoNotReturnUnauthorized(String path) {
        AtomicBoolean continued = new AtomicBoolean(false);
        GatewayFilterChain chain = exchange -> {
            continued.set(true);
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post(path).build()
        );

        filter.filter(exchange, chain).block();

        assertThat(continued).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unauthenticatedMeReturnsUnauthorized() {
        AtomicBoolean continued = new AtomicBoolean(false);
        GatewayFilterChain chain = exchange -> {
            continued.set(true);
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/me").build()
        );

        filter.filter(exchange, chain).block();

        assertThat(continued).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void realtimePathWithSubprotocolTokenIsAccepted() {
        JwtTokenService tokenService = new JwtTokenService(JWT_SECRET, 900);
        UUID userId = UUID.randomUUID();
        String token = tokenService.createAccessToken(userId);

        AtomicBoolean continued = new AtomicBoolean(false);
        GatewayFilterChain chain = exchange -> {
            continued.set(true);
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/realtime/ws")
                        .header("Sec-WebSocket-Protocol", "chanter-jwt, " + token)
                        .build()
        );

        filter.filter(exchange, chain).block();

        assertThat(continued).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void realtimePathWithoutTokenIsRejected() {
        AtomicBoolean continued = new AtomicBoolean(false);
        GatewayFilterChain chain = exchange -> {
            continued.set(true);
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/realtime/ws").build()
        );

        filter.filter(exchange, chain).block();

        assertThat(continued).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void extractTokenFromSubprotocolsTwoValueForm() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Sec-WebSocket-Protocol", "chanter-jwt, mytoken123");

        String token = JwtAuthenticationGlobalFilter.extractTokenFromSubprotocols(headers);

        assertThat(token).isEqualTo("mytoken123");
    }

    @Test
    void extractTokenFromSubprotocolsDotForm() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Sec-WebSocket-Protocol", "chanter-jwt.mytoken123");

        String token = JwtAuthenticationGlobalFilter.extractTokenFromSubprotocols(headers);

        assertThat(token).isEqualTo("mytoken123");
    }

    @Test
    void extractTokenFromSubprotocolsReturnsNullWhenAbsent() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Sec-WebSocket-Protocol", "other-protocol");

        String token = JwtAuthenticationGlobalFilter.extractTokenFromSubprotocols(headers);

        assertThat(token).isNull();
    }
}
