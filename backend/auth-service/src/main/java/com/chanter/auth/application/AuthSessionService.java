package com.chanter.auth.application;

import com.chanter.auth.domain.AuthUser;
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

    private final AuthUserRepository authUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthSessionService(
            AuthUserRepository authUserRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            @Value("${chanter.jwt.refresh-token-ttl:7d}") Duration refreshTokenTtl
    ) {
        this.authUserRepository = authUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public AuthSession register(String email, String password, String displayName) {
        String normalizedEmail = normalizeEmail(email);
        if (authUserRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        AuthUser user = new AuthUser(
                UUID.randomUUID(),
                normalizedEmail,
                passwordEncoder.encode(password),
                displayName.trim(),
                Instant.now()
        );
        try {
            authUserRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered",
                    exception
            );
        }
        return issueSession(user);
    }

    public AuthSession login(String email, String password) {
        AuthUser user = authUserRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return issueSession(user);
    }

    @Transactional
    public AuthSession refresh(String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        UUID userId = refreshTokenRepository.consumeActiveUserIdByTokenHash(tokenHash, Instant.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        AuthUser user = authUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        return issueSession(user);
    }

    public void logout(String refreshToken) {
        refreshTokenRepository.revokeByTokenHash(hashToken(refreshToken), Instant.now());
    }

    public AuthUserProfile requireUser(UUID userId) {
        return authUserRepository.findById(userId)
                .map(AuthUserProfile::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    public UUID requireUserIdFromAccessToken(String authorizationHeader) {
        return jwtTokenService.parseUserId(authorizationHeader);
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

    private AuthSession issueSession(AuthUser user) {
        String refreshToken = generateRefreshToken();
        refreshTokenRepository.save(
                UUID.randomUUID(),
                user.id(),
                hashToken(refreshToken),
                Instant.now().plus(refreshTokenTtl)
        );
        return new AuthSession(
                jwtTokenService.createAccessToken(user.id()),
                refreshToken,
                jwtTokenService.accessTokenTtlSeconds(),
                AuthUserProfile.from(user)
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
            AuthUserProfile user
    ) {
    }

    public record AuthUserProfile(UUID id, String email, String displayName) {

        static AuthUserProfile from(AuthUser user) {
            return new AuthUserProfile(user.id(), user.email(), user.displayName());
        }
    }
}
