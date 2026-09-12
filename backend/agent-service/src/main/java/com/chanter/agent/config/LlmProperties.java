package com.chanter.agent.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("chanter.llm")
public record LlmProperties(
        @DefaultValue("source-only") String defaultModelId,
        Map<String, Model> models,
        @DefaultValue("100000") long dailyTokenLimit,
        @DefaultValue("false") boolean enabled
) {
    public LlmProperties {
        models = models == null ? Map.of() : Map.copyOf(models);
    }

    public record Model(
            String label,
            String provider,
            String model,
            String baseUrl,
            String apiKey,
            @DefaultValue("8192") int maxInputTokens,
            @DefaultValue("1024") int maxOutputTokens,
            @DefaultValue("30s") Duration timeout,
            Set<UUID> allowedCourseIds,
            Price price
    ) {
        public Model {
            allowedCourseIds = allowedCourseIds == null ? Set.of() : Set.copyOf(allowedCourseIds);
        }
        @Override public String toString() { return "Model[provider=" + provider + ", credentials=redacted]"; }
    }

    /** USD per million tokens, explicitly versioned by the operator. Missing rates mean unknown cost. */
    public record Price(String version, BigDecimal input, BigDecimal output, BigDecimal cacheRead, BigDecimal cacheWrite) {}
}
