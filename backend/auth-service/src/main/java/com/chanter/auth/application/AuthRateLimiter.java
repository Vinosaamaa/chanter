package com.chanter.auth.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Simple in-memory rate limiter for single-node staging (#102).
 * Entries expire with the rate-limit window and the map is capped (SEC-20).
 */
@Component
public class AuthRateLimiter {

    private final int maxRequests;
    private final Duration window;
    private final int maxEntries;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public AuthRateLimiter(
            @Value("${chanter.auth.rate-limit.max-requests:30}") int maxRequests,
            @Value("${chanter.auth.rate-limit.window:1m}") Duration window,
            @Value("${chanter.auth.rate-limit.max-entries:10000}") int maxEntries
    ) {
        this.maxRequests = maxRequests;
        this.window = window;
        this.maxEntries = Math.max(1, maxEntries);
    }

    public void check(String key) {
        Instant now = Instant.now();
        evictExpired(now);
        WindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStart.plus(window).isBefore(now)) {
                return new WindowCounter(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        enforceBound(now);
        if (counter.count.get() > maxRequests) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many auth requests. Try again shortly.");
        }
    }

    /** Visible for tests. */
    int size() {
        return counters.size();
    }

    private void evictExpired(Instant now) {
        counters.entrySet().removeIf(entry -> entry.getValue().windowStart.plus(window).isBefore(now));
    }

    private void enforceBound(Instant now) {
        if (counters.size() <= maxEntries) {
            return;
        }
        evictExpired(now);
        while (counters.size() > maxEntries) {
            counters.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().windowStart))
                    .map(Map.Entry::getKey)
                    .ifPresent(counters::remove);
        }
    }

    private record WindowCounter(Instant windowStart, AtomicInteger count) {
    }
}
