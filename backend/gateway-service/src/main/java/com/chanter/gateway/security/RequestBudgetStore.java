package com.chanter.gateway.security;

import java.util.List;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface RequestBudgetStore {
    record Budget(String key, int limit) {}
    /** Atomically consumes all budgets, or returns the seconds until a denied budget resets. */
    Mono<Long> acquire(List<Budget> budgets);
}
