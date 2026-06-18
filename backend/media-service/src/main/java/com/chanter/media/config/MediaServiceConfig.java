package com.chanter.media.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MediaServiceConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
