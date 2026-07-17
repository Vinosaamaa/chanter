package com.chanter.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AuthRateLimiterTest {

    @Test
    void allowsRequestsWithinLimit() {
        AuthRateLimiter limiter = new AuthRateLimiter(3, Duration.ofMinutes(1), 100);
        limiter.check("login:1");
        limiter.check("login:1");
        limiter.check("login:1");
        assertThat(limiter.size()).isEqualTo(1);
    }

    @Test
    void rejectsWhenOverLimit() {
        AuthRateLimiter limiter = new AuthRateLimiter(2, Duration.ofMinutes(1), 100);
        limiter.check("login:1");
        limiter.check("login:1");
        assertThatThrownBy(() -> limiter.check("login:1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void capsDistinctKeys() {
        AuthRateLimiter limiter = new AuthRateLimiter(30, Duration.ofMinutes(1), 5);
        for (int i = 0; i < 20; i++) {
            limiter.check("key-" + i);
        }
        assertThat(limiter.size()).isLessThanOrEqualTo(5);
    }

    @Test
    void expiresStaleEntriesOnCheck() throws InterruptedException {
        AuthRateLimiter limiter = new AuthRateLimiter(30, Duration.ofMillis(50), 100);
        limiter.check("stale");
        assertThat(limiter.size()).isEqualTo(1);
        Thread.sleep(80);
        limiter.check("fresh");
        assertThat(limiter.size()).isEqualTo(1);
    }
}
