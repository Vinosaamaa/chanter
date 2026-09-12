package com.chanter.agent.config;

import com.chanter.common.auth.JwtTokenService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class AgentServiceConfig {

    @Bean
    com.chanter.agent.application.LlmChatClient llmChatClient(com.chanter.agent.application.LlmModelCatalog catalog) {
        return catalog.client(catalog.defaultModelId());
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtTokenService jwtTokenService(
            @Value("${chanter.jwt.secret}") String secret,
            @Value("${chanter.jwt.access-token-ttl:15m}") Duration accessTokenTtl
    ) {
        return new JwtTokenService(secret, accessTokenTtl.toSeconds());
    }
}
