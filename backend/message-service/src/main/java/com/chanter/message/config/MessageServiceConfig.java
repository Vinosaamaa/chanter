package com.chanter.message.config;

import com.chanter.common.auth.JwtTokenService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageServiceConfig {

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
