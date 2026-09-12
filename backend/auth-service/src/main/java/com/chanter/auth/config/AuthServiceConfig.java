package com.chanter.auth.config;

import com.chanter.common.auth.JwtTokenService;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

@Configuration
public class AuthServiceConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        // PBKDF2 preserves the complete advertised 128-character password, including UTF-8.
        var current = new Pbkdf2PasswordEncoder("", 16, 600_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        var legacy = new BCryptPasswordEncoder();
        var encoder = new DelegatingPasswordEncoder("pbkdf2-sha256-v1",
                Map.of("pbkdf2-sha256-v1", current, "bcrypt", legacy));
        encoder.setDefaultPasswordEncoderForMatches(legacy);
        return encoder;
    }

    @Bean
    JwtTokenService jwtTokenService(
            @Value("${chanter.jwt.secret}") String secret,
            @Value("${chanter.jwt.access-token-ttl:15m}") Duration accessTokenTtl
    ) {
        return new JwtTokenService(secret, accessTokenTtl.toSeconds());
    }
}
