package com.chanter.gateway.security;

import com.chanter.common.auth.AuthHeaders;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** The WebFilter limits socket identity before JWT work; the GlobalFilter limits verified users afterward. */
public final class RequestAdmissionFilter implements WebFilter, GlobalFilter, Ordered {
    private static final String REQUEST_ID = RequestAdmissionFilter.class.getName() + ".requestId";
    private final RequestBudgetStore store;
    private final SecretKeySpec key;
    private final MeterRegistry metrics;
    private long recoveryWindowStarted = System.nanoTime();
    private int recoveryRequests;

    public RequestAdmissionFilter(RequestBudgetStore store, String secret, MeterRegistry metrics) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Distributed admission requires a dedicated secret of at least 32 bytes");
        }
        this.store = store;
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.metrics = metrics;
    }

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 10; }

    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!path.startsWith("/api/v1/") || path.equals("/api/v1/auth/health")
                || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) return chain.filter(exchange);
        String requestId = UUID.randomUUID().toString();
        exchange.getAttributes().put(REQUEST_ID, requestId);
        exchange.getResponse().getHeaders().set("X-Request-Id", requestId);
        var policy = RequestBudgetPolicy.classify(exchange.getRequest().getMethod(), path);
        String client = exchange.getAttribute(ProxyBoundaryFilter.CLIENT_IP_ATTRIBUTE);
        if (client == null) return reject(exchange, policy, HttpStatus.SERVICE_UNAVAILABLE, "ADMISSION_UNAVAILABLE", 5);
        var budgets = new ArrayList<RequestBudgetStore.Budget>();
        // Recovery has an independent allowance; ordinary traffic cannot consume it.
        if (!policy.allowsBoundedRecovery()) budgets.add(budget("ip:all", client, 1200));
        budgets.add(budget("ip:" + policy.name(), client, policy.ipLimit));
        return admit(exchange, policy, budgets).flatMap(allowed -> allowed ? chain.filter(exchange) : Mono.empty());
    }

    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String user = exchange.getRequest().getHeaders().getFirst(AuthHeaders.USER_ID);
        if (user == null) return chain.filter(exchange);
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        var policy = RequestBudgetPolicy.classify(exchange.getRequest().getMethod(), path);
        var budgets = new ArrayList<RequestBudgetStore.Budget>();
        if (!policy.allowsBoundedRecovery()) budgets.add(budget("user:all", user, 600));
        budgets.add(budget("user:" + policy.name(), user, policy.userLimit));
        String tenant = RequestBudgetPolicy.tenantHint(path);
        if (tenant != null) budgets.add(budget("tenant-user:" + policy.name(), tenant + ":" + user, policy.userLimit));
        return admit(exchange, policy, budgets).flatMap(allowed -> allowed ? chain.filter(exchange) : Mono.empty());
    }

    private Mono<Boolean> admit(ServerWebExchange exchange, RequestBudgetPolicy policy, List<RequestBudgetStore.Budget> budgets) {
        // Handle store failures only. Never retry or reinterpret errors from the downstream request.
        return Mono.defer(() -> store.acquire(budgets)).defaultIfEmpty(-1L).onErrorReturn(-1L).flatMap(retry -> {
            if (retry == 0) return Mono.just(true);
            if (retry < 0 && policy.allowsBoundedRecovery() && allowRecovery()) {
                count(policy, "bounded-recovery");
                return Mono.just(true);
            }
            return reject(exchange, policy, retry < 0 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.TOO_MANY_REQUESTS,
                    retry < 0 ? "ADMISSION_UNAVAILABLE" : "RATE_LIMITED", retry < 0 ? 5 : retry).thenReturn(false);
        });
    }

    private synchronized boolean allowRecovery() {
        long now = System.nanoTime();
        if (now - recoveryWindowStarted >= 60_000_000_000L) { recoveryWindowStarted = now; recoveryRequests = 0; }
        if (recoveryRequests >= 60) return false;
        recoveryRequests++;
        return true;
    }

    private RequestBudgetStore.Budget budget(String category, String identity, int limit) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            String hash = HexFormat.of().formatHex(mac.doFinal((category + "\0" + identity).getBytes(StandardCharsets.UTF_8)));
            return new RequestBudgetStore.Budget("chanter:admission:" + hash, limit);
        } catch (GeneralSecurityException failure) { throw new IllegalStateException("Admission key generation failed", failure); }
    }

    private Mono<Void> reject(ServerWebExchange exchange, RequestBudgetPolicy policy, HttpStatus status, String code, long retry) {
        count(policy, status == HttpStatus.TOO_MANY_REQUESTS ? "limited" : "unavailable");
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl("no-store");
        response.getHeaders().set("Retry-After", Long.toString(Math.max(1, retry)));
        response.getHeaders().set("X-Content-Type-Options", "nosniff");
        String requestId = exchange.getAttributeOrDefault(REQUEST_ID, UUID.randomUUID().toString());
        String body = "{\"code\":\"" + code + "\",\"message\":\"Please wait before trying again.\",\"requestId\":\"" + requestId + "\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    private void count(RequestBudgetPolicy policy, String outcome) {
        metrics.counter("chanter.gateway.admission", "operation", policy.name(), "outcome", outcome).increment();
    }
}
