package com.chanter.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanter.common.auth.AuthHeaders;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class ProxyBoundaryFilterTest {
    @Test void stripsSpoofedAuthorityBeforeAuthenticationAndForwardsOnlyConfiguredOrigin() {
        var filter = new ProxyBoundaryFilter(new TrustedClientIdentity(List.of("172.30.45.2")), "https://study.example");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("http://gateway:8080/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress("192.0.2.8", 1234))
                .header("X-Forwarded-For", "198.51.100.1").header("X-Forwarded-Proto", "http")
                .header("X-Forwarded-Host", "evil.example").header("Forwarded", "for=198.51.100.1;host=evil.example")
                .header("CF-Connecting-IP", "198.51.100.2").header("X-Real-IP", "198.51.100.3")
                .header(AuthHeaders.USER_ID, "forged-user").header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "forged-service")
                .header("Authorization", "Bearer retained-for-jwt-validation").build());
        var forwarded = new AtomicReference<ServerWebExchange>();
        filter.filter(exchange, next -> { forwarded.set(next); return Mono.empty(); }).block();
        var request = forwarded.get().getRequest();
        assertThat(request.getHeaders().getFirst("X-Forwarded-For")).isEqualTo("192.0.2.8");
        assertThat(request.getHeaders().getFirst("X-Forwarded-Proto")).isEqualTo("https");
        assertThat(request.getHeaders().getFirst("X-Forwarded-Host")).isEqualTo("study.example");
        for (var header : List.of("Forwarded", "CF-Connecting-IP", "X-Real-IP", AuthHeaders.USER_ID, AuthHeaders.INTERNAL_SERVICE_TOKEN)) {
            assertThat(request.getHeaders().containsHeader(header)).as(header).isFalse();
        }
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer retained-for-jwt-validation");
        assertThat(forwarded.get().getAttribute(ProxyBoundaryFilter.CLIENT_IP_ATTRIBUTE).toString()).isEqualTo("192.0.2.8");
    }

    @Test void malformedTrustedProxyIdentityNeverReachesApplication() {
        var filter = new ProxyBoundaryFilter(new TrustedClientIdentity(List.of("172.30.45.2")), "https://study.example");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress("172.30.45.2", 1234)).header("X-Forwarded-For", "a, b").build());
        filter.filter(exchange, next -> Mono.error(new AssertionError("Invalid identity reached application"))).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange.getResponse().getBodyAsString().block()).doesNotContain("a, b");
    }
}
