package com.chanter.gateway.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;

/** Atomic, expiring fixed windows on the existing private, single Redis instance. */
public final class RedisRequestBudgetStore implements RequestBudgetStore {
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local wait = 0
            local window = tonumber(ARGV[1])
            for i, key in ipairs(KEYS) do
                local count = tonumber(redis.call('GET', key) or '0')
                if count >= tonumber(ARGV[i + 1]) then
                    local ttl = redis.call('PTTL', key)
                    if ttl < 0 then redis.call('PEXPIRE', key, window); ttl = window end
                    wait = math.max(wait, ttl, 1)
                end
            end
            if wait > 0 then return math.ceil(wait / 1000) end
            for _, key in ipairs(KEYS) do
                local count = redis.call('INCR', key)
                if count == 1 then redis.call('PEXPIRE', key, window) end
            end
            return 0
            """, Long.class);
    private final ReactiveStringRedisTemplate redis;
    private final long windowMillis;

    public RedisRequestBudgetStore(ReactiveStringRedisTemplate redis, long windowMillis) {
        if (windowMillis < 1000 || windowMillis > 60_000) throw new IllegalArgumentException("Admission windows must be between one and sixty seconds");
        this.redis = redis;
        this.windowMillis = windowMillis;
    }

    @Override public Mono<Long> acquire(List<Budget> budgets) {
        if (budgets.isEmpty() || budgets.size() > 3 || budgets.stream().anyMatch(budget -> budget.limit() < 1)) {
            return Mono.error(new IllegalArgumentException("Invalid admission budgets"));
        }
        List<String> arguments = new ArrayList<>();
        arguments.add(Long.toString(windowMillis));
        budgets.forEach(budget -> arguments.add(Integer.toString(budget.limit())));
        return redis.execute(ACQUIRE, budgets.stream().map(Budget::key).toList(), arguments)
                .single().timeout(Duration.ofSeconds(1));
    }
}
