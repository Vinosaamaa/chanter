package com.chanter.auth.config;

import com.chanter.common.auth.JwtTokenService;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthServiceConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtTokenService jwtTokenService(
            @Value("${chanter.jwt.secret}") String secret,
            @Value("${chanter.jwt.access-token-ttl:15m}") Duration accessTokenTtl
    ) {
        return new JwtTokenService(secret, accessTokenTtl.toSeconds());
    }
}
