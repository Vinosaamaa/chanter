package com.chanter.gateway.security;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InvalidJwtException;
import com.chanter.common.auth.JwtTokenService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final String ACTUATOR_PREFIX = "/actuator/";
    private static final String REALTIME_API_PREFIX = "/api/v1/realtime/";
    private static final String OAUTH_AUTH_PREFIX = "/api/v1/auth/oauth/";
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/v1/auth/health",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/verify-email"
    );

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationGlobalFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return continueWithoutSpoofedUserId(exchange, chain);
        }

        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (isPublicPath(path)) {
            return continueWithoutSpoofedUserId(exchange, chain);
        }

        if (!path.startsWith("/api/v1/")) {
            return continueWithoutSpoofedUserId(exchange, chain);
        }

        String authorizationHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        try {
            ResolvedIdentity identity = resolveIdentity(path, exchange.getRequest(), authorizationHeader);
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(AuthHeaders.USER_ID);
                        headers.set(AuthHeaders.USER_ID, identity.userId().toString());
                        if (identity.bearerToken() != null) {
                            headers.set(HttpHeaders.AUTHORIZATION, AuthHeaders.BEARER_PREFIX + identity.bearerToken());
                        }
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (InvalidJwtException exception) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    static boolean isPublicPath(String path) {
        if (path.startsWith(ACTUATOR_PREFIX)) {
            return true;
        }
        if (PUBLIC_AUTH_PATHS.contains(path)) {
            return true;
        }
        // /oauth/providers, /oauth/{provider}/start, /oauth/google/callback, …
        return path.startsWith(OAUTH_AUTH_PREFIX);
    }

    private static Mono<Void> continueWithoutSpoofedUserId(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.remove(AuthHeaders.USER_ID))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private ResolvedIdentity resolveIdentity(String path, ServerHttpRequest request, String authorizationHeader) {
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            return new ResolvedIdentity(jwtTokenService.parseUserId(authorizationHeader), null);
        }

        if (path.startsWith(REALTIME_API_PREFIX)) {
            String token = extractTokenFromSubprotocols(request.getHeaders());
            if (token != null) {
                UUID userId = jwtTokenService.parseUserId(AuthHeaders.BEARER_PREFIX + token);
                return new ResolvedIdentity(userId, token);
            }
        }

        throw new InvalidJwtException("Missing or invalid Authorization header");
    }

    static String extractTokenFromSubprotocols(HttpHeaders headers) {
        List<String> protocols = headers.get("Sec-WebSocket-Protocol");
        if (protocols == null || protocols.isEmpty()) {
            return null;
        }
        for (String header : protocols) {
            for (String part : header.split(",")) {
                String trimmed = part.trim();
                if (trimmed.startsWith("chanter-jwt.")) {
                    return trimmed.substring("chanter-jwt.".length()).trim();
                }
            }
        }
        for (String header : protocols) {
            String[] parts = header.split(",");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("chanter-jwt".equals(parts[i].trim())) {
                    String candidate = parts[i + 1].trim();
                    if (!candidate.isBlank()) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private record ResolvedIdentity(UUID userId, String bearerToken) {
    }
}
