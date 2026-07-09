package com.chanter.realtime.web;

import com.chanter.common.auth.AuthHeaders;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthenticatedUserWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!path.startsWith("/api/v1/direct-message-calls/")) {
            return chain.filter(exchange);
        }

        String userIdHeader = exchange.getRequest().getHeaders().getFirst(AuthHeaders.USER_ID);
        if (userIdHeader == null || userIdHeader.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            UUID userId = UUID.fromString(userIdHeader);
            exchange.getAttributes().put("authenticatedUserId", userId);
            return chain.filter(exchange);
        } catch (IllegalArgumentException exception) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }
}
