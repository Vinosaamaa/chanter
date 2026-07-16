package com.chanter.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
        "chanter.internal-service-token=test-internal-service-token-for-auth",
        "chanter.oauth.google.client-id=test-google-client",
        "chanter.oauth.google.client-secret=test-google-secret"
})
@ActiveProfiles("test")
class OAuthStatePkceTest {

    @Autowired
    private OAuthAuthService oauthAuthService;

    @Autowired
    private OAuthPendingStore pendingStore;

    // --- OAuthPendingStore unit tests ---

    @Test
    void createReturnsDifferentStatesEachTime() {
        OAuthPendingStore store = new OAuthPendingStore();
        OAuthPendingStore.PendingEntry a = store.create("google");
        OAuthPendingStore.PendingEntry b = store.create("google");
        assertThat(a.state()).isNotEqualTo(b.state());
        assertThat(a.codeVerifier()).isNotEqualTo(b.codeVerifier());
    }

    @Test
    void consumeReturnsEntryAndRemovesIt() {
        OAuthPendingStore store = new OAuthPendingStore();
        OAuthPendingStore.PendingEntry created = store.create("google");
        assertThat(store.size()).isEqualTo(1);

        OAuthPendingStore.PendingEntry consumed = store.consume(created.state());
        assertThat(consumed).isNotNull();
        assertThat(consumed.codeVerifier()).isEqualTo(created.codeVerifier());
        assertThat(store.size()).isZero();
    }

    @Test
    void consumeReturnsNullForUnknownState() {
        OAuthPendingStore store = new OAuthPendingStore();
        assertThat(store.consume("nonexistent-state")).isNull();
    }

    @Test
    void consumeReturnsNullForNullOrBlankState() {
        OAuthPendingStore store = new OAuthPendingStore();
        assertThat(store.consume(null)).isNull();
        assertThat(store.consume("")).isNull();
        assertThat(store.consume("   ")).isNull();
    }

    @Test
    void consumeReturnsNullForExpiredEntry() throws Exception {
        OAuthPendingStore store = new OAuthPendingStore();
        OAuthPendingStore.PendingEntry created = store.create("google");

        // Backdating the entry in the store to simulate expiry.
        Field storeField = OAuthPendingStore.class.getDeclaredField("store");
        storeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, OAuthPendingStore.PendingEntry> map =
                (ConcurrentHashMap<String, OAuthPendingStore.PendingEntry>) storeField.get(store);

        Instant expired = Instant.now().minusSeconds(OAuthPendingStore.TTL_SECONDS + 1);
        map.put(created.state(), new OAuthPendingStore.PendingEntry(
                created.state(), created.codeVerifier(), created.provider(), expired));

        assertThat(store.consume(created.state())).isNull();
    }

    @Test
    void stateIsSingleUse() {
        OAuthPendingStore store = new OAuthPendingStore();
        OAuthPendingStore.PendingEntry created = store.create("google");
        assertThat(store.consume(created.state())).isNotNull();
        assertThat(store.consume(created.state())).isNull();
    }

    // --- authorizationUrl includes state and code_challenge ---

    @Test
    void authorizationUrlContainsStateAndCodeChallenge() {
        String url = oauthAuthService.authorizationUrl("google");
        assertThat(url)
                .contains("state=")
                .contains("code_challenge=")
                .contains("code_challenge_method=S256");
    }

    @Test
    void authorizationUrlCreatesNewPendingEntryEachCall() {
        int before = pendingStore.size();
        oauthAuthService.authorizationUrl("google");
        oauthAuthService.authorizationUrl("google");
        assertThat(pendingStore.size()).isGreaterThanOrEqualTo(before + 2);
    }

    // --- completeGoogleLogin state validation ---

    @Test
    void completeGoogleLoginRequiresNonBlankState() {
        assertThatThrownBy(() -> oauthAuthService.completeGoogleLogin("some-code", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> oauthAuthService.completeGoogleLogin("some-code", ""))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void completeGoogleLoginWithUnknownStateReturnsForbidden() {
        assertThatThrownBy(() -> oauthAuthService.completeGoogleLogin("some-code", "bogus-state"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException ex = (ResponseStatusException) e;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).containsIgnoringCase("Invalid or expired OAuth state");
                });
    }

    @Test
    void completeGoogleLoginRequiresNonBlankCode() {
        OAuthPendingStore.PendingEntry pending = pendingStore.create("google");
        assertThatThrownBy(() -> oauthAuthService.completeGoogleLogin(null, pending.state()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        // state should still be consumed even when code fails — but the current impl checks
        // code before state lookup to fail fast, so verify state is still present.
    }

    @Test
    void completeGoogleLoginConsumesPendingEntryOnStateValidationPath() {
        OAuthPendingStore.PendingEntry pending = pendingStore.create("google");
        String state = pending.state();

        // First attempt: state is valid so the pending entry is consumed, then the HTTP call to
        // Google fails (test credentials are fake). Any exception is acceptable here.
        try {
            oauthAuthService.completeGoogleLogin("any-code", state);
        } catch (Exception ignored) {
            // expected — real Google rejects test credentials
        }

        // Second attempt: state was already consumed → must be FORBIDDEN (single-use invariant).
        assertThatThrownBy(() -> oauthAuthService.completeGoogleLogin("any-code", state))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException ex = (ResponseStatusException) e;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).containsIgnoringCase("Invalid or expired OAuth state");
                });
    }
}
