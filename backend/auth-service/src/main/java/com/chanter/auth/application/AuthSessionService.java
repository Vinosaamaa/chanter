package com.chanter.auth.application;

import com.chanter.auth.domain.AuthUser;
import com.chanter.auth.moderation.ModerationRestrictions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.chanter.common.auth.JwtTokenService;

@Service
public class AuthSessionService {

    static final String NEUTRAL_REGISTER_MESSAGE =
            "If this email can be used, check your inbox for next steps.";

    // Every login runs both supported work factors, including missing and passwordless accounts (SEC-16).
    static final String DUMMY_PASSWORD_HASH =
            "{pbkdf2-sha256-v1}a02bf7ebdc59dc9305f55009849ace566786d5ae9a01e5b8ed626bd91156ab8a1fdbf7636c9419b52446ccdb2fce9872";
    static final String DUMMY_BCRYPT_HASH = "$2b$10$UmakdiX3qQt/PTm0vHM/iOIRL3j8/Yy1jq0dyjHg79Og4QqH/tWkK";

    private final AuthUserRepository authUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final ProductionAuthService productionAuthService;
    private final ModerationRestrictions moderation;
    private final Duration refreshTokenTtl;
    private final boolean requireEmailVerification;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthSessionService(
            AuthUserRepository authUserRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            ProductionAuthService productionAuthService,
            ModerationRestrictions moderation,
            @Value("${chanter.jwt.refresh-token-ttl:7d}") Duration refreshTokenTtl,
            @Value("${chanter.auth.require-email-verification:false}") boolean requireEmailVerification
    ) {
        this.authUserRepository = authUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.productionAuthService = productionAuthService;
        this.moderation = moderation;
        this.refreshTokenTtl = refreshTokenTtl;
        this.requireEmailVerification = requireEmailVerification;
    }

    @Transactional
    public RegisterResult registerWithStatus(String email, String password, String displayName) {
        return registerWithStatus(email, password, displayName, "");
    }

    @Transactional
    public RegisterResult registerWithStatus(String email, String password, String displayName, String userAgent) {
        String normalizedEmail = normalizeEmail(email);
        var existing = authUserRepository.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            // Neutral response (SEC-15): never reveal that the email is taken.
            sendExistingAccountNextSteps(existing.get());
            return new RegisterResult(null, true, NEUTRAL_REGISTER_MESSAGE);
        }
        boolean verified = !requireEmailVerification;
        AuthUser user = new AuthUser(
                UUID.randomUUID(),
                normalizedEmail,
                passwordEncoder.encode(password),
                displayName.trim(),
                verified,
                Instant.now()
        );
        try {
            authUserRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            sendExistingAccountNextSteps(authUserRepository.findByEmail(normalizedEmail).orElseThrow(() -> exception));
            return new RegisterResult(null, true, NEUTRAL_REGISTER_MESSAGE);
        }
        if (requireEmailVerification) {
            productionAuthService.sendEmailVerification(user);
            return new RegisterResult(null, true, NEUTRAL_REGISTER_MESSAGE);
        }
        return new RegisterResult(issueSession(user, userAgent), false, null);
    }

    private void sendExistingAccountNextSteps(AuthUser user) {
        if (user.emailVerified()) {
            productionAuthService.notifyExistingAccountRegisterAttempt(user);
        } else {
            productionAuthService.sendEmailVerification(user);
        }
    }

    @Transactional
    public AuthSession login(String email, String password) {
        return login(email, password, "");
    }

    @Transactional
    public AuthSession login(String email, String password, String userAgent) {
        AuthUser user = authUserRepository.findByEmail(normalizeEmail(email)).orElse(null);
        String passwordHash = user != null && user.passwordHash() != null
                ? user.passwordHash()
                : DUMMY_PASSWORD_HASH;
        boolean currentFormat = passwordHash.startsWith("{pbkdf2-sha256-v1}");
        boolean bcryptInputAllowed = password.getBytes(StandardCharsets.UTF_8).length <= 72;
        // An overlong guess must still perform BCrypt work, but can never authenticate a legacy hash.
        String bcryptInput = bcryptInputAllowed ? password : "non-account-timing-placeholder";
        boolean legacyMatches = passwordEncoder.matches(bcryptInput,
                currentFormat ? DUMMY_BCRYPT_HASH : passwordHash);
        boolean currentMatches;
        try {
            currentMatches = passwordEncoder.matches(password, currentFormat ? passwordHash : DUMMY_PASSWORD_HASH);
        } catch (IllegalArgumentException malformedHash) {
            passwordEncoder.matches(password, DUMMY_PASSWORD_HASH);
            currentMatches = false;
        }
        boolean passwordMatches = currentFormat ? currentMatches : bcryptInputAllowed && legacyMatches;
        if (user == null || user.passwordHash() == null || !passwordMatches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        refreshTokenRepository.lockUser(user.id());
        // Password hashing is expensive. Lock only after verification, then reject a password changed by a concurrent reset.
        user = authUserRepository.findById(user.id()).orElse(null);
        if (user == null || !passwordHash.equals(user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (requireEmailVerification && !user.emailVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Email is not verified. Check your inbox for the verification link."
            );
        }
        return issueSession(user, userAgent);
    }

    public AuthSession refresh(String refreshToken) {
        String replacement = generateRefreshToken();
        var session = refreshTokenRepository.rotate(hashToken(refreshToken), UUID.randomUUID(), hashToken(replacement), Instant.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        AuthUser user = authUserRepository.findById(session.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        moderation.requireActiveAccount(user.id());
        return new AuthSession(jwtTokenService.createAccessToken(user.id(), session.id()), replacement,
                jwtTokenService.accessTokenTtlSeconds(), AuthUserProfile.from(user), session.expiresAt());
    }

    public void logout(String refreshToken) {
        refreshTokenRepository.revokeByTokenHash(hashToken(refreshToken), Instant.now());
    }

    public List<BrowserSession> listSessions(UUID userId, String refreshToken) {
        UUID currentId = refreshToken == null ? null : refreshTokenRepository
                .findSessionIdByTokenHash(hashToken(refreshToken)).orElse(null);
        return refreshTokenRepository.findActiveSessions(userId, Instant.now()).stream()
                .map(session -> new BrowserSession(session.id(), session.createdAt(), session.lastUsedAt(),
                        session.expiresAt(), session.userAgent(), session.id().equals(currentId)))
                .toList();
    }

    public boolean revokeSession(UUID userId, UUID sessionId, String refreshToken) {
        if (!refreshTokenRepository.revokeSession(userId, sessionId, Instant.now())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        return refreshToken != null && refreshTokenRepository.findSessionIdByTokenHash(hashToken(refreshToken))
                .map(sessionId::equals).orElse(false);
    }

    public AuthUserProfile requireUser(UUID userId) {
        moderation.requireActiveAccount(userId);
        return authUserRepository.findById(userId)
                .map(AuthUserProfile::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    public UUID requireUserIdFromAccessToken(String authorizationHeader) {
        UUID user = jwtTokenService.parseUserId(authorizationHeader);
        moderation.requireActiveAccount(user);
        return user;
    }

    public JwtTokenService.AccessSession requireActiveAccessSession(String authorizationHeader) {
        var token = jwtTokenService.parseAccessSession(authorizationHeader);
        moderation.requireActiveAccount(token.userId());
        var session = refreshTokenRepository.findActiveSessions(token.userId(), Instant.now()).stream()
                .filter(candidate -> candidate.id().equals(token.sessionId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session is no longer active"));
        Instant expiry = token.expiresAt().isBefore(session.expiresAt()) ? token.expiresAt() : session.expiresAt();
        return new JwtTokenService.AccessSession(token.userId(), token.sessionId(), expiry);
    }

    public List<AuthUserProfile> findPublicProfiles(List<UUID> requestedUserIds) {
        List<UUID> distinctIds = requestedUserIds.stream().distinct().toList();
        Map<UUID, AuthUser> usersById = new LinkedHashMap<>();
        for (AuthUser user : authUserRepository.findByIds(distinctIds)) {
            usersById.put(user.id(), user);
        }

        return distinctIds.stream()
                .map(usersById::get)
                .filter(java.util.Objects::nonNull)
                .map(AuthUserProfile::from)
                .toList();
    }

    public Optional<AuthUserProfile> findProfileByEmail(String email) {
        return authUserRepository.findByEmail(normalizeEmail(email)).map(AuthUserProfile::from);
    }

    public AuthSession issueSessionForUser(AuthUser user) {
        return issueSession(user, "");
    }

    public AuthSession issueSessionForUser(AuthUser user, String userAgent) {
        return issueSession(user, userAgent);
    }

    private AuthSession issueSession(AuthUser user, String userAgent) {
        moderation.requireActiveAccount(user.id());
        String refreshToken = generateRefreshToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(refreshTokenTtl);
        String agent = userAgent == null ? "" : userAgent.substring(0, Math.min(userAgent.length(), 255));
        UUID sessionId = UUID.randomUUID();
        refreshTokenRepository.createSession(
                sessionId,
                user.id(),
                UUID.randomUUID(),
                hashToken(refreshToken),
                now, expiresAt, agent
        );
        return new AuthSession(
                jwtTokenService.createAccessToken(user.id(), sessionId),
                refreshToken,
                jwtTokenService.accessTokenTtlSeconds(),
                AuthUserProfile.from(user), expiresAt
        );
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    public record AuthSession(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            AuthUserProfile user,
            Instant refreshExpiresAt
    ) {
    }

    public record AuthUserProfile(UUID id, String email, String displayName, boolean emailVerified) {

        static AuthUserProfile from(AuthUser user) {
            return new AuthUserProfile(user.id(), user.email(), user.displayName(), user.emailVerified());
        }
    }

    public record RegisterResult(AuthSession session, boolean verificationRequired, String message) {
    }

    public record BrowserSession(UUID id, Instant createdAt, Instant lastUsedAt, Instant expiresAt,
                                 String userAgent, boolean current) {}
}
