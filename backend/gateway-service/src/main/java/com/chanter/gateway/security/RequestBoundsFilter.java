package com.chanter.gateway.security;

import com.chanter.common.auth.AuthHeaders;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Bounds each instance's memory and active work after authentication and shared admission. */
public final class RequestBoundsFilter implements GlobalFilter, Ordered {
    private final int ordinaryBytes;
    private final int uploadBytes;
    private final Duration bodyTimeout;
    private final Semaphore ordinary;
    private final Semaphore uploads = new Semaphore(2);
    private final Semaphore downloads = new Semaphore(8);
    private final Semaphore ai = new Semaphore(6);
    private final Semaphore sockets = new Semaphore(128);
    private final Map<String, Integer> activeUsers = new HashMap<>();

    public RequestBoundsFilter() { this(256 * 1024, 11 * 1024 * 1024, 32, Duration.ofSeconds(60)); }

    RequestBoundsFilter(int ordinaryBytes, int uploadBytes, int concurrency, Duration bodyTimeout) {
        this.ordinaryBytes = ordinaryBytes;
        this.uploadBytes = uploadBytes;
        this.ordinary = new Semaphore(concurrency);
        this.bodyTimeout = bodyTimeout;
    }

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 20; }

    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!path.startsWith("/api/v1/") || path.equals("/api/v1/auth/health")) return chain.filter(exchange);
        var policy = RequestBudgetPolicy.classify(exchange.getRequest().getMethod(), path);
        boolean socket = policy == RequestBudgetPolicy.RECONNECT && org.springframework.http.HttpMethod.GET.equals(exchange.getRequest().getMethod())
                && "websocket".equalsIgnoreCase(exchange.getRequest().getHeaders().getUpgrade());
        int maximum = policy == RequestBudgetPolicy.UPLOAD ? uploadBytes : ordinaryBytes;
        if (exchange.getRequest().getHeaders().getContentLength() > maximum) return reject(exchange, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE");
        Semaphore capacity = socket ? sockets : switch (policy) {
            case UPLOAD -> uploads;
            case DOWNLOAD -> downloads;
            case AI -> ai;
            default -> ordinary;
        };
        String user = exchange.getRequest().getHeaders().getFirst(AuthHeaders.USER_ID);
        String identity = (socket ? "socket" : policy.name()) + ":" + (user != null ? user
                : exchange.getAttributeOrDefault(ProxyBoundaryFilter.CLIENT_IP_ATTRIBUTE, "unknown"));
        return Mono.defer(() -> {
            if (!capacity.tryAcquire()) return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE, "REQUEST_CAPACITY");
            int userMaximum = socket ? 4 : switch (policy) {
                case UPLOAD -> 1;
                case AI, DOWNLOAD -> 2;
                case READ -> 12;
                // Browsers and shared networks can race signup requests; the shared minute budget still applies.
                case AUTH, REGISTRATION -> 8;
                default -> 4;
            };
            if (!claimUser(identity, userMaximum)) {
                capacity.release();
                return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE, "REQUEST_CAPACITY");
            }
            Mono<byte[]> body = DataBufferUtils.join(exchange.getRequest().getBody(), maximum).map(buffer -> {
                try {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return bytes;
                } finally { DataBufferUtils.release(buffer); }
            }).defaultIfEmpty(new byte[0]).timeout(bodyTimeout);
            return body.onErrorResume(DataBufferLimitException.class, failure -> reject(exchange, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE").then(Mono.empty()))
                    .onErrorResume(TimeoutException.class, failure -> reject(exchange, HttpStatus.REQUEST_TIMEOUT, "REQUEST_TIMEOUT").then(Mono.empty()))
                    .flatMap(bytes -> {
                        var request = new ServerHttpRequestDecorator(exchange.getRequest()) {
                            @Override public Flux<DataBuffer> getBody() {
                                return Flux.defer(() -> Flux.just(exchange.getResponse().bufferFactory().wrap(bytes)));
                            }
                        };
                        Mono<Void> response = chain.filter(exchange.mutate().request(request).build());
                        if (socket) return response; // Authenticated realtime owns heartbeat and idle connection policy.
                        return response.timeout(policy == RequestBudgetPolicy.DOWNLOAD ? Duration.ofMinutes(5) : Duration.ofMinutes(2))
                                .onErrorResume(TimeoutException.class, failure -> exchange.getResponse().isCommitted()
                                        ? Mono.error(failure) : reject(exchange, HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT"));
                    }).doFinally(signal -> { releaseUser(identity); capacity.release(); });
        });
    }

    private synchronized boolean claimUser(String identity, int maximum) {
        int count = activeUsers.getOrDefault(identity, 0);
        if (count >= maximum) return false;
        activeUsers.put(identity, count + 1);
        return true;
    }

    private synchronized void releaseUser(String identity) {
        activeUsers.computeIfPresent(identity, (key, count) -> count <= 1 ? null : count - 1);
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String code) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl("no-store");
        response.getHeaders().set("X-Content-Type-Options", "nosniff");
        if (status == HttpStatus.SERVICE_UNAVAILABLE || status == HttpStatus.GATEWAY_TIMEOUT) response.getHeaders().set("Retry-After", "5");
        byte[] body = ("{\"code\":\"" + code + "\",\"message\":\"The request exceeds the current service limit.\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
