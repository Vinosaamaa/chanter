package com.chanter.realtime.web;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.common.auth.InvalidJwtException;
import com.chanter.common.auth.JwtTokenService;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthenticatedUserWebFilter implements WebFilter {

    private final JwtTokenService jwtTokenService;

    public AuthenticatedUserWebFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!path.startsWith("/api/v1/direct-message-calls/")) {
            return chain.filter(exchange);
        }

        String authorizationHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            UUID userId = jwtTokenService.parseUserId(authorizationHeader);
            exchange.getAttributes().put(AuthRequestAttributes.USER_ID, userId);
            return chain.filter(exchange);
        } catch (InvalidJwtException exception) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }
}
