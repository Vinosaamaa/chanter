package com.chanter.community.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommunityServiceConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
