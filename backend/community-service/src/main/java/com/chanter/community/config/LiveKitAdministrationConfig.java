package com.chanter.community.config;

import io.livekit.server.RoomServiceClient;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@Profile("!test")
public class LiveKitAdministrationConfig {
    @Bean
    RoomServiceClient liveKitAdministration(LiveKitProperties properties,
            @Value("${LIVEKIT_HTTP_URL:http://localhost:7880}") String url) {
        return RoomServiceClient.create(url, properties.apiKey(), properties.apiSecret(), false,
                builder -> builder.connectTimeout(1, TimeUnit.SECONDS).callTimeout(2, TimeUnit.SECONDS));
    }
}
