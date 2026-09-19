package com.chanter.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class RequestBoundsFilterTest {
    @Test void rejectsDeclaredAndChunkedOversizeBeforeForwardingAnything() {
        var filter = new RequestBoundsFilter(16, 32, 1, Duration.ofSeconds(1));
        var declared = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login").contentLength(17).build());
        filter.filter(declared, next -> Mono.error(new AssertionError("Oversize body reached backend"))).block();
        assertThat(declared.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        var factory = new DefaultDataBufferFactory();
        var chunked = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login")
                .body(Flux.just(factory.wrap(new byte[10]), factory.wrap(new byte[10]))));
        filter.filter(chunked, next -> Mono.error(new AssertionError("Partial body reached backend"))).block();
        assertThat(chunked.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test void slowBodiesExpireAndCancellationReleasesCapacity() {
        var filter = new RequestBoundsFilter(16, 32, 1, Duration.ofMillis(40));
        var slow = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login").body(Flux.never()));
        filter.filter(slow, next -> Mono.error(new AssertionError("Incomplete body reached backend"))).block(Duration.ofSeconds(2));
        assertThat(slow.getResponse().getStatusCode()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);
        var held = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login").body("{}"));
        var pending = filter.filter(held, next -> Mono.never()).subscribe();
        var saturated = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login").body("{}"));
        filter.filter(saturated, next -> Mono.error(new AssertionError("Capacity exceeded"))).block();
        assertThat(saturated.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        pending.dispose();
        var accepted = new AtomicBoolean();
        var retried = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login").body("{}"));
        filter.filter(retried, next -> { accepted.set(true); return Mono.empty(); }).block();
        assertThat(accepted).isTrue();
    }

    @Test void validPayloadIsForwardedExactlyOnceWithoutLoss() {
        var filter = new RequestBoundsFilter(16, 32, 1, Duration.ofSeconds(1));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login").body("{\"a\":1}"));
        var result = new StringBuilder();
        filter.filter(exchange, next -> next.getRequest().getBody().doOnNext(buffer -> {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            result.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            org.springframework.core.io.buffer.DataBufferUtils.release(buffer);
        }).then()).block();
        assertThat(result.toString()).isEqualTo("{\"a\":1}");
    }
}
