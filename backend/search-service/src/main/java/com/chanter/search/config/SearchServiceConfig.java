package com.chanter.search.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        CommunityServiceClientProperties.class,
        MediaServiceClientProperties.class,
        MessageServiceClientProperties.class
})
public class SearchServiceConfig {
}
