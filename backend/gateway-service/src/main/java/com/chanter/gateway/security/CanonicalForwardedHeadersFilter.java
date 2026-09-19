package com.chanter.gateway.security;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/** Restore only server-validated identity after Gateway removes forwarded and hop-by-hop headers. */
public final class CanonicalForwardedHeadersFilter implements HttpHeadersFilter, Ordered {
    private final ProxyBoundaryFilter boundary;

    public CanonicalForwardedHeadersFilter(ProxyBoundaryFilter boundary) { this.boundary = boundary; }

    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

    @Override public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
        HttpHeaders output = new HttpHeaders();
        output.addAll(input);
        boundary.applyForwardedHeaders(output, exchange);
        return output;
    }

    @Override public boolean supports(Type type) { return type == Type.REQUEST; }
}
