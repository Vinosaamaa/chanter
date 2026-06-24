package com.chanter.analytics.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        CommunityServiceClientProperties.class,
        MessageServiceClientProperties.class,
        AgentServiceClientProperties.class
})
public class AnalyticsServiceConfig {
}
