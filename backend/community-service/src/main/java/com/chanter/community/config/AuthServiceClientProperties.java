package com.chanter.community.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chanter.auth-service")
public record AuthServiceClientProperties(
        @NotBlank String baseUrl,
        @Positive int connectTimeoutSeconds,
        @Positive int readTimeoutSeconds,
        @NotBlank String serviceToken
) {

    public Duration connectTimeout() {
        return Duration.ofSeconds(connectTimeoutSeconds);
    }

    public Duration readTimeout() {
        return Duration.ofSeconds(readTimeoutSeconds);
    }
}
