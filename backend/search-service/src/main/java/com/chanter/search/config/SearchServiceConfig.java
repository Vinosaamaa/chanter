package com.chanter.search.config;

import com.chanter.common.auth.JwtTokenService;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        CommunityServiceClientProperties.class,
        MediaServiceClientProperties.class,
        MessageServiceClientProperties.class
})
public class SearchServiceConfig {

    @Bean
    JwtTokenService jwtTokenService(
            @Value("${chanter.jwt.secret}") String secret,
            @Value("${chanter.jwt.access-token-ttl:15m}") Duration accessTokenTtl
    ) {
        return new JwtTokenService(secret, accessTokenTtl.toSeconds());
    }
}
