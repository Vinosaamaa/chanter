package com.chanter.community.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chanter.livekit")
public record LiveKitProperties(
        @NotBlank String url,
        @NotBlank String apiKey,
        @NotBlank String apiSecret
) {
}
