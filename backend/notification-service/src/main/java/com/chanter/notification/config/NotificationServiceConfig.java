package com.chanter.notification.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationServiceConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
