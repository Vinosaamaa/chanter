package com.chanter.community.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chanter.livekit")
public record LiveKitProperties(
        String url,
        String apiKey,
        String apiSecret
) {
}
