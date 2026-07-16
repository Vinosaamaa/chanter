package com.chanter.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chanter.common.auth.JwtTokenService;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthSessionServiceLoginTimingTest {

    private AuthUserRepository authUserRepository;
    private PasswordEncoder passwordEncoder;
    private AuthSessionService authSessionService;

    @BeforeEach
    void setUp() {
        authUserRepository = mock(AuthUserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authSessionService = new AuthSessionService(
                authUserRepository,
                mock(RefreshTokenRepository.class),
                passwordEncoder,
                new JwtTokenService(
                        "test-jwt-secret-for-login-timing-at-least-32-chars",
                        900L
                ),
                mock(ProductionAuthService.class),
                Duration.ofDays(7),
                false
        );
    }

    @Test
    void unknownEmailStillRunsPasswordMatchesAgainstDummyHash() {
        when(authUserRepository.findByEmail("missing@study.local")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authSessionService.login("missing@study.local", "password123"))
                .hasMessageContaining("Invalid email or password");

        verify(passwordEncoder).matches(eq("password123"), eq(AuthSessionService.DUMMY_PASSWORD_HASH));
    }
}
