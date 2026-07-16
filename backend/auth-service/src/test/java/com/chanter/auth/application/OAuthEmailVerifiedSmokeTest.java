package com.chanter.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chanter.auth.domain.AuthUser;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
        "chanter.internal-service-token=test-internal-service-token-for-auth",
        "chanter.oauth.google.client-id=test-google-client",
        "chanter.oauth.google.client-secret=test-google-secret"
})
@ActiveProfiles("test")
class OAuthEmailVerifiedSmokeTest {

    @Autowired
    private OAuthAuthService oauthAuthService;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private OAuthAccountRepository oauthAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void unverifiedGoogleEmailCannotLinkExistingAccount() {
        String email = "victim-" + UUID.randomUUID() + "@study.local";
        AuthUser victim = new AuthUser(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode("password123"),
                "Victim",
                true,
                Instant.now()
        );
        authUserRepository.save(victim);

        String attackerSubject = "google-attacker-" + UUID.randomUUID();
        assertThatThrownBy(() -> oauthAuthService.sessionFromGoogleUserInfo(Map.of(
                        "sub", attackerSubject,
                        "email", email,
                        "name", "Attacker",
                        "email_verified", false
                )))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getReason()).containsIgnoringCase("verified");
                });

        assertThat(oauthAccountRepository.findUserId("google", attackerSubject)).isEmpty();
        assertThat(authUserRepository.findByEmail(email).orElseThrow().id()).isEqualTo(victim.id());
    }

    @Test
    void missingEmailVerifiedClaimIsRejected() {
        assertThatThrownBy(() -> oauthAuthService.sessionFromGoogleUserInfo(Map.of(
                        "sub", "google-missing-" + UUID.randomUUID(),
                        "email", "unverified-" + UUID.randomUUID() + "@study.local",
                        "name", "No Claim"
                )))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void verifiedGoogleEmailProvisionsNewAccount() {
        String email = "oauth-new-" + UUID.randomUUID() + "@study.local";
        String subject = "google-new-" + UUID.randomUUID();

        AuthSessionService.AuthSession session = oauthAuthService.sessionFromGoogleUserInfo(Map.of(
                "sub", subject,
                "email", email,
                "name", "New OAuth User",
                "email_verified", true
        ));

        assertThat(session.user().email()).isEqualTo(email);
        assertThat(session.user().emailVerified()).isTrue();
        assertThat(oauthAccountRepository.findUserId("google", subject)).contains(session.user().id());
    }

    @Test
    void verifiedGoogleEmailCanLinkExistingUnverifiedAccountAndMarksVerified() {
        String email = "link-" + UUID.randomUUID() + "@study.local";
        AuthUser existing = new AuthUser(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode("password123"),
                "Pending Verify",
                false,
                Instant.now()
        );
        authUserRepository.save(existing);
        String subject = "google-link-" + UUID.randomUUID();

        AuthSessionService.AuthSession session = oauthAuthService.sessionFromGoogleUserInfo(Map.of(
                "sub", subject,
                "email", email,
                "name", "Pending Verify",
                "email_verified", true
        ));

        assertThat(session.user().id()).isEqualTo(existing.id());
        assertThat(session.user().emailVerified()).isTrue();
        assertThat(oauthAccountRepository.findUserId("google", subject)).contains(existing.id());
    }

    @Test
    void alreadyLinkedGoogleSubjectSignsInWithoutRequiringEmailVerifiedClaim() {
        String email = "linked-" + UUID.randomUUID() + "@study.local";
        AuthUser user = new AuthUser(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode("password123"),
                "Already Linked",
                true,
                Instant.now()
        );
        authUserRepository.save(user);
        String subject = "google-already-" + UUID.randomUUID();
        oauthAccountRepository.link(UUID.randomUUID(), user.id(), "google", subject);

        AuthSessionService.AuthSession session = oauthAuthService.sessionFromGoogleUserInfo(Map.of(
                "sub", subject,
                "email", email,
                "name", "Already Linked",
                "email_verified", false
        ));

        assertThat(session.user().id()).isEqualTo(user.id());
    }
}
