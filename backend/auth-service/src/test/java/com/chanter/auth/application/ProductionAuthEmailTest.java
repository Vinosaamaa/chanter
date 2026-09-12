package com.chanter.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chanter.auth.domain.AuthUser;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

class ProductionAuthEmailTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void verificationAndResetUseTheConfiguredOriginAndTheExactTokenExpiry(boolean reset) {
        var users = mock(AuthUserRepository.class);
        var tokens = mock(AuthEmailTokenRepository.class);
        var email = new RecordingEmailSender();
        Instant[] tokenExpiry = new Instant[1];
        doAnswer(invocation -> {
            tokenExpiry[0] = invocation.getArgument(4);
            return null;
        }).when(tokens).save(any(), any(), any(), any(), any());
        var user = new AuthUser(UUID.randomUUID(), "learner@example.test", "hash", "Learner", false, Instant.now());
        when(users.findByEmail(user.email())).thenReturn(Optional.of(user));
        var auth = new ProductionAuthService(users, tokens, mock(RefreshTokenRepository.class), email,
                mock(PasswordEncoder.class), Duration.ofHours(2), "https://learning.chanter.test/");

        Instant earliestExpiry = Instant.now().plus(Duration.ofHours(2));
        if (reset) {
            auth.requestPasswordReset(user.email());
        } else {
            auth.sendEmailVerification(user);
        }

        Instant latestExpiry = Instant.now().plus(Duration.ofHours(2));
        assertThat(email.expiresAt).isEqualTo(tokenExpiry[0]).isBetween(earliestExpiry, latestExpiry);
        assertThat(email.subject.contains("Chanter")).isTrue();
        assertThat(email.body.contains("Chanter")).isTrue();
        assertThat(email.body.contains("https://learning.chanter.test/" + (reset ? "reset-password" : "verify-email") + "?token=")).isTrue();
        assertThat(email.body.contains("expires")).isTrue();
        assertThat(email.body.contains("localhost")).isFalse();
    }

    private static class RecordingEmailSender implements EmailSender {
        private String subject;
        private String body;
        private Instant expiresAt;

        @Override
        public void send(String recipient, String subject, String body) {
            this.subject = subject;
            this.body = body;
        }

        @Override
        public void send(String recipient, String subject, String body, Instant expiresAt) {
            send(recipient, subject, body);
            this.expiresAt = expiresAt;
        }
    }
}
