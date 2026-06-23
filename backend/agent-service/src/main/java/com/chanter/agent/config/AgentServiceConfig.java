package com.chanter.agent.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentServiceConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
