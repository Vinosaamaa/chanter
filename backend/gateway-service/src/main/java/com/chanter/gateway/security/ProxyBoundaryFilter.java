package com.chanter.gateway.security;

import com.chanter.common.auth.AuthHeaders;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Runs before gateway authentication; untrusted HTTP headers never become authority. */
public final class ProxyBoundaryFilter implements WebFilter, Ordered {
    public static final String CLIENT_IP_ATTRIBUTE = ProxyBoundaryFilter.class.getName() + ".clientIp";
    private final TrustedClientIdentity identities;
    private final URI publicOrigin;

    public ProxyBoundaryFilter(TrustedClientIdentity identities, String baseUrl) {
        this.identities = identities;
        publicOrigin = baseUrl == null || baseUrl.isBlank() ? null : URI.create(baseUrl);
        if (publicOrigin != null && (!"https".equals(publicOrigin.getScheme()) || publicOrigin.getHost() == null
                || publicOrigin.getUserInfo() != null || publicOrigin.getQuery() != null || publicOrigin.getFragment() != null
                || !(publicOrigin.getPath().isEmpty() || "/".equals(publicOrigin.getPath())))) {
            throw new IllegalArgumentException("Gateway public origin must be an HTTPS origin");
        }
    }

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        final String client;
        try {
            client = identities.resolve(exchange.getRequest().getRemoteAddress(),
                    exchange.getRequest().getHeaders().get("X-Forwarded-For"));
        } catch (IllegalArgumentException invalid) {
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().setCacheControl("no-store");
            byte[] body = "{\"code\":\"INVALID_PROXY_IDENTITY\",\"message\":\"Invalid forwarded client identity.\"}".getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        }
        exchange.getAttributes().put(CLIENT_IP_ATTRIBUTE, client);
        var request = exchange.getRequest().mutate().headers(headers -> {
            headers.headerNames().stream().filter(ProxyBoundaryFilter::untrustedHeader).toList().forEach(headers::remove);
            applyForwardedHeaders(headers, exchange);
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    void applyForwardedHeaders(HttpHeaders headers, ServerWebExchange exchange) {
        String client = exchange.getAttribute(CLIENT_IP_ATTRIBUTE);
        if (client == null) throw new IllegalStateException("Client identity must be resolved before forwarding");
        headers.headerNames().stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith("x-forwarded-")
                || name.equalsIgnoreCase("Forwarded")).toList().forEach(headers::remove);
        headers.set("X-Forwarded-For", client);
        if (publicOrigin != null) {
            headers.set("X-Forwarded-Proto", publicOrigin.getScheme());
            headers.set("X-Forwarded-Host", publicOrigin.getRawAuthority());
        }
    }

    private static boolean untrustedHeader(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("x-forwarded-") || lower.startsWith("cf-") || lower.equals("forwarded")
                || lower.equals("x-real-ip") || lower.equals("x-internal-service-token")
                || lower.equals("x-chanter-origin-token") || lower.equals("x-original-url") || lower.equals("x-rewrite-url")
                || name.equalsIgnoreCase(AuthHeaders.USER_ID) || name.equalsIgnoreCase(AuthHeaders.INTERNAL_SERVICE_TOKEN);
    }
}
