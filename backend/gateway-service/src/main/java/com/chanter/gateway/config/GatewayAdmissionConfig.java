package com.chanter.gateway.config;

import com.chanter.gateway.security.RedisRequestBudgetStore;
import com.chanter.gateway.security.RequestAdmissionFilter;
import com.chanter.gateway.security.RequestBudgetStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration
@ConditionalOnProperty(name = "chanter.edge.limits-enabled", havingValue = "true")
public class GatewayAdmissionConfig {
    @Bean RequestBudgetStore requestBudgetStore(ReactiveStringRedisTemplate redis) {
        return new RedisRequestBudgetStore(redis, 60_000);
    }

    @Bean RequestAdmissionFilter requestAdmissionFilter(RequestBudgetStore store, MeterRegistry metrics,
            @Value("${chanter.edge.key-secret:}") String secret) {
        return new RequestAdmissionFilter(store, secret, metrics);
    }
}
