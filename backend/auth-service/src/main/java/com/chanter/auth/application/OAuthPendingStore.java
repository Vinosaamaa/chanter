package com.chanter.auth.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory store for pending OAuth flows keyed by {@code state}.
 *
 * <p>Each entry is single-use and expires after {@value #TTL_SECONDS} seconds (10 minutes).
 * Expired entries are purged lazily on every {@link #create} call.
 *
 * <p>Not persisted — a service restart invalidates all in-flight OAuth flows, which is
 * acceptable for a SPA that controls the browser session.
 */
@Component
public class OAuthPendingStore {

    static final long TTL_SECONDS = 600;

    private final ConcurrentHashMap<String, PendingEntry> store = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Creates and persists a new pending OAuth flow for the given provider.
     *
     * @return the new {@link PendingEntry} containing the generated {@code state} and
     *         {@code codeVerifier}
     */
    public PendingEntry create(String provider) {
        String state = generateState();
        String codeVerifier = generateCodeVerifier();
        PendingEntry entry = new PendingEntry(state, codeVerifier, provider, Instant.now());
        store.put(state, entry);
        purgeExpired();
        return entry;
    }

    /**
     * Removes and returns the pending entry for {@code state}.
     *
     * @return the entry, or {@code null} if {@code state} is unknown or the entry has expired
     */
    public PendingEntry consume(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        PendingEntry entry = store.remove(state);
        if (entry == null) {
            return null;
        }
        if (isExpired(entry)) {
            return null;
        }
        return entry;
    }

    /** Package-visible for testing — returns current store size. */
    int size() {
        return store.size();
    }

    private void purgeExpired() {
        store.entrySet().removeIf(e -> isExpired(e.getValue()));
    }

    private boolean isExpired(PendingEntry entry) {
        return entry.createdAt().plusSeconds(TTL_SECONDS).isBefore(Instant.now());
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Generates a PKCE {@code code_verifier}: 64 random bytes base64url-encoded without padding
     * → 86 URL-safe characters, well within the RFC 7636 range of 43–128.
     */
    private String generateCodeVerifier() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record PendingEntry(String state, String codeVerifier, String provider, Instant createdAt) {}
}
