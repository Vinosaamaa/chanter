package com.chanter.auth.application;

import com.chanter.auth.domain.AuthUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductionAuthService {

    public static final String PURPOSE_EMAIL_VERIFY = "EMAIL_VERIFY";
    public static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";

    private final AuthUserRepository authUserRepository;
    private final AuthEmailTokenRepository emailTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final Duration tokenTtl;
    private final String publicBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public ProductionAuthService(
            AuthUserRepository authUserRepository,
            AuthEmailTokenRepository emailTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            EmailSender emailSender,
            PasswordEncoder passwordEncoder,
            @Value("${chanter.auth.email-token-ttl:24h}") Duration tokenTtl,
            @Value("${chanter.public-base-url:http://localhost:5173}") String publicBaseUrl
    ) {
        this.authUserRepository = authUserRepository;
        this.emailTokenRepository = emailTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
        this.tokenTtl = tokenTtl;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @Transactional
    public void sendEmailVerification(AuthUser user) {
        String rawToken = createToken(user.id(), PURPOSE_EMAIL_VERIFY);
        String link = publicBaseUrl + "/verify-email?token=" + rawToken;
        emailSender.send(
                user.email(),
                "Verify your Chanter email",
                "Welcome to Chanter.\n\nVerify your email:\n" + link + "\n\nThis link expires in "
                        + tokenTtl.toHours() + " hours."
        );
    }

    /**
     * Out-of-band notice when someone tries to register an email that already exists (SEC-15).
     * The HTTP register response stays neutral; this email tells the owner without revealing
     * existence to the caller.
     */
    public void notifyExistingAccountRegisterAttempt(AuthUser user) {
        emailSender.send(
                user.email(),
                "Chanter account signup attempt",
                "Someone tried to create a Chanter account using this email.\n\n"
                        + "If this was you, sign in instead of registering again.\n"
                        + "If it was not you, you can ignore this message — no new account was created."
        );
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        TokenUser tokenUser = requireToken(rawToken, PURPOSE_EMAIL_VERIFY);
        authUserRepository.markEmailVerified(tokenUser.userId());
        emailTokenRepository.markUsed(tokenUser.tokenId(), Instant.now());
    }

    @Transactional
    public void requestPasswordReset(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        AuthUser user = authUserRepository.findByEmail(normalized).orElse(null);
        // Always succeed to avoid account enumeration.
        if (user == null) {
            return;
        }
        emailTokenRepository.invalidateActiveForUser(user.id(), PURPOSE_PASSWORD_RESET, Instant.now());
        String rawToken = createToken(user.id(), PURPOSE_PASSWORD_RESET);
        String link = publicBaseUrl + "/reset-password?token=" + rawToken;
        emailSender.send(
                user.email(),
                "Reset your Chanter password",
                "Reset your password:\n" + link + "\n\nIf you did not request this, ignore this email."
        );
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        TokenUser tokenUser = requireToken(rawToken, PURPOSE_PASSWORD_RESET);
        authUserRepository.updatePasswordHash(tokenUser.userId(), passwordEncoder.encode(newPassword));
        emailTokenRepository.markUsed(tokenUser.tokenId(), Instant.now());
        refreshTokenRepository.revokeAllForUser(tokenUser.userId(), Instant.now());
    }

    private TokenUser requireToken(String rawToken, String purpose) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token is required");
        }
        AuthEmailTokenRepository.TokenRecord record = emailTokenRepository
                .findActiveByTokenHash(hashToken(rawToken), purpose, Instant.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));
        return new TokenUser(record.id(), record.userId());
    }

    private String createToken(UUID userId, String purpose) {
        String rawToken = generateToken();
        emailTokenRepository.save(
                UUID.randomUUID(),
                userId,
                hashToken(rawToken),
                purpose,
                Instant.now().plus(tokenTtl)
        );
        return rawToken;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private record TokenUser(UUID tokenId, UUID userId) {
    }
}
