package com.chanter.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class ModerationAccessConfiguration {
    @Bean
    @Profile("!test")
    ModerationAccess moderationAccess(@Value("${AUTH_SERVICE_URL:http://localhost:8081}") String authUrl,
            @Value("${chanter.internal-service-token}") String token, ObjectMapper mapper) {
        InternalServiceTokens.requireBytes(token);
        return new ModerationAccess(URI.create(authUrl), token, mapper);
    }

    /** Isolated service tests use synthetic users. Real-stack verification runs the live implementation. */
    @Bean
    @Profile("test")
    ModerationAccess testModerationAccess(ObjectMapper mapper) {
        return new ModerationAccess(URI.create("http://localhost"), "test-only-unused", mapper) {
            @Override public void requireAllowed(UUID user, List<Target> targets) { }
            @Override public java.util.Set<Target> allowedSources(UUID user, List<Target> targets) { return java.util.Set.copyOf(targets); }
        };
    }
}
