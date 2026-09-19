package com.chanter.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanter.common.auth.AuthHeaders;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class RequestAdmissionFilterTest {
    private static final String SECRET = "test-only-key-with-at-least-32-bytes";

    @Test void ipAdmissionHashesIdentityAndReturnsStableRetryWithoutForwarding() {
        var budgets = new AtomicReference<List<RequestBudgetStore.Budget>>();
        var filter = new RequestAdmissionFilter(values -> { budgets.set(values); return Mono.just(42L); }, SECRET, new SimpleMeterRegistry());
        var exchange = request("/api/v1/auth/login");
        filter.filter(exchange, (WebFilterChain) next -> Mono.error(new AssertionError("Denied request reached upstream"))).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("42");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("RATE_LIMITED").doesNotContain("192.0.2.1", "forged-user");
        assertThat(budgets.get()).hasSize(2).allSatisfy(budget -> assertThat(budget.key()).doesNotContain("192.0.2.1", SECRET));
    }

    @Test void redisFailureClosesNormalAdmissionButLeavesABoundedLogoutRecoveryPath() {
        var filter = new RequestAdmissionFilter(values -> Mono.error(new IllegalStateException("private endpoint must not leak")), SECRET, new SimpleMeterRegistry());
        var login = request("/api/v1/auth/login");
        filter.filter(login, (WebFilterChain) next -> Mono.error(new AssertionError("Store outage bypassed login protection"))).block();
        assertThat(login.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(login.getResponse().getBodyAsString().block()).doesNotContain("private endpoint");
        int accepted = 0;
        for (int i = 0; i < 65; i++) {
            var forwarded = new AtomicBoolean();
            var logout = request("/api/v1/auth/logout");
            filter.filter(logout, (WebFilterChain) next -> { forwarded.set(true); return Mono.empty(); }).block();
            if (forwarded.get()) accepted++;
            else assertThat(logout.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
        assertThat(accepted).isEqualTo(60);
    }

    @Test void authenticatedUserAndTenantBudgetsCannotReplaceTheGlobalUserBudget() {
        var budgets = new AtomicReference<List<RequestBudgetStore.Budget>>();
        var filter = new RequestAdmissionFilter(values -> { budgets.set(values); return Mono.just(0L); }, SECRET, new SimpleMeterRegistry());
        var exchange = request("/api/v1/study-servers/11111111-1111-1111-1111-111111111111/search");
        var verified = exchange.mutate().request(exchange.getRequest().mutate().headers(headers -> headers.set(AuthHeaders.USER_ID,
                "22222222-2222-2222-2222-222222222222")).build()).build();
        filter.filter(verified, (GatewayFilterChain) next -> Mono.empty()).block();
        assertThat(budgets.get()).hasSize(3);
        assertThat(budgets.get().getFirst().limit()).isEqualTo(600);
        assertThat(budgets.get()).allSatisfy(budget -> assertThat(budget.key()).doesNotContain("11111111", "22222222", "forged-user"));
    }

    private MockServerWebExchange request(String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path).header(AuthHeaders.USER_ID, "forged-user").build());
        exchange.getAttributes().put(ProxyBoundaryFilter.CLIENT_IP_ATTRIBUTE, "192.0.2.1");
        return exchange;
    }
}
