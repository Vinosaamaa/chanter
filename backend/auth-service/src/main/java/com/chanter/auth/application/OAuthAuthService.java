package com.chanter.auth.application;

import com.chanter.auth.domain.AuthUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
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
            @Value("${chanter.public-base-url:http://localhost:5173}") String publicBaseUrl,
            @Value("${chanter.oauth.google.client-id:}") String googleClientId,
            @Value("${chanter.oauth.google.client-secret:}") String googleClientSecret
    ) {
        this.authUserRepository = authUserRepository;
        this.authSessionService = authSessionService;
        this.passwordEncoder = passwordEncoder;
        this.oauthAccountRepository = oauthAccountRepository;
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

    public String authorizationUrl(String provider) {
        if (!"google".equalsIgnoreCase(provider) || googleClientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OAuth provider is not configured");
        }
        String redirectUri = publicBaseUrl + "/oauth/callback/google";
        return UriComponentsBuilder
                .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", googleClientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("access_type", "online")
                .queryParam("prompt", "select_account")
                .build(true)
                .toUriString();
    }

    @Transactional
    public AuthSessionService.AuthSession completeGoogleLogin(String code) {
        if (googleClientId.isBlank() || googleClientSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Google OAuth is not configured");
        }
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authorization code is required");
        }

        String redirectUri = publicBaseUrl + "/oauth/callback/google";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", googleClientId);
        form.add("client_secret", googleClientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

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
        if (profile == null || profile.get("sub") == null || profile.get("email") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google profile lookup failed");
        }

        String subject = String.valueOf(profile.get("sub"));
        String email = String.valueOf(profile.get("email")).trim().toLowerCase(Locale.ROOT);
        String displayName = profile.get("name") == null
                ? email
                : String.valueOf(profile.get("name")).trim();

        return oauthAccountRepository.findUserId("google", subject)
                .flatMap(authUserRepository::findById)
                .map(authSessionService::issueSessionForUser)
                .orElseGet(() -> provisionGoogleUser(subject, email, displayName));
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

    public record ProviderInfo(String id, String label, String authorizationUrl) {
    }
}
