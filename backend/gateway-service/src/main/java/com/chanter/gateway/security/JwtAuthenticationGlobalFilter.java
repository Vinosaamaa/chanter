package com.chanter.gateway.security;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InvalidJwtException;
import com.chanter.common.auth.JwtTokenService;
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
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/v1/auth/health",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    );

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationGlobalFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        if (!path.startsWith("/api/v1/")) {
            return chain.filter(exchange);
        }

        String authorizationHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        try {
            UUID userId = jwtTokenService.parseUserId(authorizationHeader);
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(AuthHeaders.USER_ID);
                        headers.set(AuthHeaders.USER_ID, userId.toString());
                    })
                    .build();
            return chain.filter(exchange.mutate().request(request).build());
        } catch (InvalidJwtException exception) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static boolean isPublicPath(String path) {
        if (path.startsWith(ACTUATOR_PREFIX)) {
            return true;
        }
        return PUBLIC_AUTH_PATHS.contains(path);
    }
}
