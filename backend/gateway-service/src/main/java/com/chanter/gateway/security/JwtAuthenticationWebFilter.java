package com.chanter.gateway.security;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InvalidJwtException;
import com.chanter.common.auth.JwtTokenService;
import java.util.List;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JwtAuthenticationWebFilter implements WebFilter {

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/actuator/",
            "/api/v1/auth/health",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    );

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationWebFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
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
            ServerWebExchange authenticatedExchange = exchange.mutate()
                    .request(builder -> builder.header(AuthHeaders.USER_ID, userId.toString()))
                    .build();
            return chain.filter(authenticatedExchange);
        } catch (InvalidJwtException exception) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private static boolean isPublicPath(String path) {
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
