package com.chanter.gateway.security;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InvalidJwtException;
import com.chanter.common.auth.JwtTokenService;
import java.net.URI;
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
import org.springframework.web.util.UriComponentsBuilder;
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
            UUID userId = resolveUserId(path, exchange.getRequest(), authorizationHeader);
            ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(AuthHeaders.USER_ID);
                        headers.set(AuthHeaders.USER_ID, userId.toString());
                    });
            if (exchange.getRequest().getQueryParams().containsKey("access_token")) {
                URI sanitizedUri = UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                        .replaceQueryParam("access_token")
                        .build(true)
                        .toUri();
                requestBuilder.uri(sanitizedUri);
            }
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
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

    private UUID resolveUserId(String path, ServerHttpRequest request, String authorizationHeader) {
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            return jwtTokenService.parseUserId(authorizationHeader);
        }

        if (path.startsWith(REALTIME_API_PREFIX)) {
            String accessToken = request.getQueryParams().getFirst("access_token");
            if (accessToken != null && !accessToken.isBlank()) {
                return jwtTokenService.parseUserId(AuthHeaders.BEARER_PREFIX + accessToken.trim());
            }
        }

        throw new InvalidJwtException("Missing or invalid Authorization header");
    }
}
