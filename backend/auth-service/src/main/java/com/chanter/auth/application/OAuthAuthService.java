package com.chanter.auth.application;

import com.chanter.auth.domain.AuthUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OAuthAuthService {

    private final AuthUserRepository authUserRepository;
    private final AuthSessionService authSessionService;
    private final PasswordEncoder passwordEncoder;
    private final OAuthAccountRepository oauthAccountRepository;
    private final OAuthPendingStore pendingStore;
    private final String publicBaseUrl;
    private final String googleClientId;
    private final String googleClientSecret;
    private final RestClient restClient = RestClient.create();
    private final SecureRandom secureRandom = new SecureRandom();

    public OAuthAuthService(
            AuthUserRepository authUserRepository,
            AuthSessionService authSessionService,
            PasswordEncoder passwordEncoder,
            OAuthAccountRepository oauthAccountRepository,
            OAuthPendingStore pendingStore,
            @Value("${chanter.public-base-url:http://localhost:5173}") String publicBaseUrl,
            @Value("${chanter.oauth.google.client-id:}") String googleClientId,
            @Value("${chanter.oauth.google.client-secret:}") String googleClientSecret
    ) {
        this.authUserRepository = authUserRepository;
        this.authSessionService = authSessionService;
        this.passwordEncoder = passwordEncoder;
        this.oauthAccountRepository = oauthAccountRepository;
        this.pendingStore = pendingStore;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        this.googleClientId = googleClientId == null ? "" : googleClientId.trim();
        this.googleClientSecret = googleClientSecret == null ? "" : googleClientSecret.trim();
    }

    public List<ProviderInfo> listProviders() {
        List<ProviderInfo> providers = new ArrayList<>();
        if (!googleClientId.isBlank() && !googleClientSecret.isBlank()) {
            providers.add(new ProviderInfo("google", "Google", authorizationUrl("google")));
        }
        return providers;
    }

    /**
     * Builds the OAuth authorization URL for the given provider.
     *
     * <p>Each call creates a new pending entry (state + PKCE code_verifier) in
     * {@link OAuthPendingStore}. The {@code state} and {@code code_challenge} are added
     * as query parameters so Google echoes state back and the PKCE exchange is enforced.
     */
    public String authorizationUrl(String provider) {
        if (!"google".equalsIgnoreCase(provider) || googleClientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OAuth provider is not configured");
        }
        String redirectUri = publicBaseUrl + "/oauth/callback/google";

        OAuthPendingStore.PendingEntry pending = pendingStore.create(provider);
        String codeChallenge = computeS256Challenge(pending.codeVerifier());

        return UriComponentsBuilder
                .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", googleClientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("access_type", "online")
                .queryParam("prompt", "select_account")
                .queryParam("state", pending.state())
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();
    }

    /**
     * Completes the Google OAuth login flow.
     *
     * <p>Validates {@code state} against the {@link OAuthPendingStore} (single-use, 10-min TTL),
     * then performs the token exchange with PKCE {@code code_verifier}.
     *
     * @param code  the authorization code returned by Google
     * @param state the {@code state} parameter returned by Google in the redirect
     * @throws ResponseStatusException 400 if code or state is blank
     * @throws ResponseStatusException 403 if state is unknown or expired (SEC-09)
     */
    @Transactional
    public AuthSessionService.AuthSession completeGoogleLogin(String code, String state) {
        if (googleClientId.isBlank() || googleClientSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Google OAuth is not configured");
        }
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authorization code is required");
        }
        if (state == null || state.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth state is required");
        }

        OAuthPendingStore.PendingEntry pending = pendingStore.consume(state);
        if (pending == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or expired OAuth state");
        }

        String redirectUri = publicBaseUrl + "/oauth/callback/google";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", googleClientId);
        form.add("client_secret", googleClientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        form.add("code_verifier", pending.codeVerifier());

        Map<?, ?> tokenResponse = restClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        if (tokenResponse == null || tokenResponse.get("access_token") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google token exchange failed");
        }
        String accessToken = String.valueOf(tokenResponse.get("access_token"));

        Map<?, ?> profile = restClient.get()
                .uri("https://openidconnect.googleapis.com/v1/userinfo")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
        return sessionFromGoogleUserInfo(profile);
    }

    /**
     * Resolves or provisions a Chanter session from a Google userinfo payload.
     * Requires {@code email_verified == true} before any email-based link/provision (SEC-05).
     * Already-linked Google subjects may sign in without re-checking email verification.
     */
    AuthSessionService.AuthSession sessionFromGoogleUserInfo(Map<?, ?> profile) {
        if (profile == null || profile.get("sub") == null || profile.get("email") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google profile lookup failed");
        }

        String subject = String.valueOf(profile.get("sub"));
        String email = String.valueOf(profile.get("email")).trim().toLowerCase(Locale.ROOT);
        String displayName = profile.get("name") == null
                ? email
                : String.valueOf(profile.get("name")).trim();

        var linked = oauthAccountRepository.findUserId("google", subject)
                .flatMap(authUserRepository::findById);
        if (linked.isPresent()) {
            return authSessionService.issueSessionForUser(linked.get());
        }

        requireGoogleEmailVerified(profile);
        return provisionGoogleUser(subject, email, displayName);
    }

    private static void requireGoogleEmailVerified(Map<?, ?> profile) {
        Object verifiedClaim = profile.get("email_verified");
        boolean verified = verifiedClaim instanceof Boolean booleanValue
                ? booleanValue
                : "true".equalsIgnoreCase(String.valueOf(verifiedClaim));
        if (!verified) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Google email must be verified before signing in with OAuth"
            );
        }
    }

    private AuthSessionService.AuthSession provisionGoogleUser(String subject, String email, String displayName) {
        AuthUser existing = authUserRepository.findByEmail(email).orElse(null);
        AuthUser user = existing;
        if (user == null) {
            byte[] randomPassword = new byte[32];
            secureRandom.nextBytes(randomPassword);
            user = new AuthUser(
                    UUID.randomUUID(),
                    email,
                    passwordEncoder.encode(HexFormat.of().formatHex(randomPassword)),
                    displayName.isBlank() ? email : displayName,
                    true,
                    Instant.now()
            );
            try {
                authUserRepository.save(user);
            } catch (DataIntegrityViolationException exception) {
                user = authUserRepository.findByEmail(email)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Unable to create OAuth user",
                                exception
                        ));
            }
        } else if (!user.emailVerified()) {
            // Safe: caller required Google email_verified before provision.
            authUserRepository.markEmailVerified(user.id());
            user = authUserRepository.findById(user.id()).orElse(user);
        }

        try {
            oauthAccountRepository.link(UUID.randomUUID(), user.id(), "google", subject);
        } catch (DataIntegrityViolationException ignored) {
            // already linked
        }
        return authSessionService.issueSessionForUser(user);
    }

    /**
     * Computes the PKCE {@code code_challenge} for the given {@code code_verifier} using S256.
     * {@code BASE64URL(SHA256(ASCII(code_verifier)))} per RFC 7636 §4.2.
     */
    private static String computeS256Challenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record ProviderInfo(String id, String label, String authorizationUrl) {
    }
}
