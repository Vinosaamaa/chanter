package com.chanter.search.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chanter.community-service")
public record CommunityServiceClientProperties(
        String baseUrl,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {
    public Duration connectTimeout() {
        return Duration.ofSeconds(connectTimeoutSeconds);
    }

    public Duration readTimeout() {
        return Duration.ofSeconds(readTimeoutSeconds);
    }
}
