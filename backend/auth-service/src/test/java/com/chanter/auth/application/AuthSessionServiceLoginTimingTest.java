package com.chanter.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;

import com.chanter.common.auth.JwtTokenService;
import com.chanter.auth.domain.AuthUser;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthSessionServiceLoginTimingTest {

    private AuthUserRepository authUserRepository;
    private PasswordEncoder passwordEncoder;
    private AuthSessionService authSessionService;
    private ProductionAuthService productionAuthService;
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        authUserRepository = mock(AuthUserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        productionAuthService = mock(ProductionAuthService.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        authSessionService = new AuthSessionService(
                authUserRepository,
                refreshTokenRepository,
                passwordEncoder,
                new JwtTokenService(
                        "test-jwt-secret-for-login-timing-at-least-32-chars",
                        900L
                ),
                productionAuthService,
                Duration.ofDays(7),
                false
        );
    }

    @Test
    void repeatRegistrationReissuesVerificationForAnUnverifiedAccount() {
        var user = new AuthUser(UUID.randomUUID(), "pending@study.local", "hash", "Pending", false, Instant.now());
        when(authUserRepository.findByEmail(user.email())).thenReturn(Optional.of(user));
        var result = authSessionService.registerWithStatus(user.email(), "password123", "Pending");
        org.assertj.core.api.Assertions.assertThat(result.verificationRequired()).isTrue();
        org.assertj.core.api.Assertions.assertThat(result.session()).isNull();
        verify(productionAuthService).sendEmailVerification(user);
    }

    @Test
    void unknownEmailStillRunsPasswordMatchesAgainstDummyHash() {
        when(authUserRepository.findByEmail("missing@study.local")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authSessionService.login("missing@study.local", "password123"))
                .hasMessageContaining("Invalid email or password");

        verify(passwordEncoder).matches(eq("password123"), eq(AuthSessionService.DUMMY_PASSWORD_HASH));
    }

    @Test
    void failedLoginRunsBothPasswordWorkFactorsForMissingPasswordlessLegacyAndCurrentAccounts() {
        for (String storedHash : java.util.Arrays.asList(null, "", "$2a$10$legacy-fixture", "{pbkdf2-sha256-v1}current-fixture")) {
            clearInvocations(passwordEncoder);
            var user = storedHash == null ? null : new AuthUser(UUID.randomUUID(), "timing@study.local",
                    storedHash.isEmpty() ? null : storedHash, "Learner", true, Instant.now());
            when(authUserRepository.findByEmail("timing@study.local")).thenReturn(Optional.ofNullable(user));

            assertThatThrownBy(() -> authSessionService.login("timing@study.local", "wrong-password"))
                    .hasMessageContaining("Invalid email or password");

            var hashes = ArgumentCaptor.forClass(String.class);
            verify(passwordEncoder, times(2)).matches(anyString(), hashes.capture());
            assertThat(hashes.getAllValues().get(0)).startsWith("$2");
            assertThat(hashes.getAllValues().get(1)).startsWith("{pbkdf2-sha256-v1}");
        }
    }

    @Test
    void overlongLegacyGuessStillRunsBcryptWorkWithoutAcceptingATruncatedPassword() {
        var user = new AuthUser(UUID.randomUUID(), "legacy@study.local", "$2a$10$legacy-fixture", "Learner", true, Instant.now());
        when(authUserRepository.findByEmail(user.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        String guess = "界".repeat(80);
        assertThatThrownBy(() -> authSessionService.login(user.email(), guess)).hasMessageContaining("Invalid email or password");
        verify(refreshTokenRepository, never()).lockUser(user.id());
        var passwords = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder, times(2)).matches(passwords.capture(), anyString());
        assertThat(passwords.getAllValues().get(0).getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(72);
        assertThat(passwords.getAllValues().get(1)).isEqualTo(guess);
    }

    @Test
    void passwordVerificationDoesNotLockTheAccountAndAConcurrentResetRejectsTheOldPassword() {
        var original = new AuthUser(UUID.randomUUID(), "learner@study.local", "old-hash", "Learner", true, Instant.now());
        var reset = new AuthUser(original.id(), original.email(), "new-hash", original.displayName(), true, original.createdAt());
        when(authUserRepository.findByEmail(original.email())).thenReturn(Optional.of(original));
        when(authUserRepository.findById(original.id())).thenReturn(Optional.of(reset));
        when(passwordEncoder.matches("old-password", "old-hash")).thenAnswer(invocation -> {
            verify(refreshTokenRepository, never()).lockUser(original.id());
            return true;
        });
        assertThatThrownBy(() -> authSessionService.login(original.email(), "old-password"))
                .hasMessageContaining("Invalid email or password");
        verify(passwordEncoder).matches("old-password", "old-hash");
        verify(refreshTokenRepository).lockUser(original.id());
    }
}
