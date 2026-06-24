package com.chanter.analytics.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chanter.community-service")
public record CommunityServiceClientProperties(
        @NotBlank String baseUrl,
        @Positive int connectTimeoutSeconds,
        @Positive int readTimeoutSeconds
) {
    public Duration connectTimeout() {
        return Duration.ofSeconds(connectTimeoutSeconds);
    }

    public Duration readTimeout() {
        return Duration.ofSeconds(readTimeoutSeconds);
    }
}
