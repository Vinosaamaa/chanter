package com.chanter.auth.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Simple in-memory rate limiter for single-node staging (#102).
 */
@Component
public class AuthRateLimiter {

    private final int maxRequests;
    private final Duration window;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public AuthRateLimiter(
            @Value("${chanter.auth.rate-limit.max-requests:30}") int maxRequests,
            @Value("${chanter.auth.rate-limit.window:1m}") Duration window
    ) {
        this.maxRequests = maxRequests;
        this.window = window;
    }

    public void check(String key) {
        Instant now = Instant.now();
        WindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStart.plus(window).isBefore(now)) {
                return new WindowCounter(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        if (counter.count.get() > maxRequests) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many auth requests. Try again shortly.");
        }
    }

    private record WindowCounter(Instant windowStart, AtomicInteger count) {
    }
}
