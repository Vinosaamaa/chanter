package com.chanter.auth.moderation;

import com.chanter.auth.application.AuthUserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OperatorVerification {
    private final JdbcTemplate jdbc;
    private final OperatorAccess access;
    private final OperatorFactor factor;
    private final AuthUserRepository users;
    private final PasswordEncoder passwords;
    private final ModerationAudit audit;
    private final SecureRandom random = new SecureRandom();

    public OperatorVerification(JdbcTemplate jdbc, OperatorAccess access, OperatorFactor factor,
            AuthUserRepository users, PasswordEncoder passwords, ModerationAudit audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.factor = factor;
        this.users = users;
        this.passwords = passwords;
        this.audit = audit;
    }

    // Invalid attempts must commit their durable rate limit. Other failures roll back normally.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Enrollment enroll(String authorization, String password, UUID correlation) {
        var operator = access.requireRole(authorization);
        FactorRow row = lock(operator.userId());
        access.requireRole(authorization);
        attempt(operator.userId(), row);
        requirePassword(operator.userId(), password);
        if (row.confirmed()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Operator factor already enrolled");
        byte[] secret = factor.newSecret();
        String encrypted = factor.encrypt(operator.userId(), secret);
        jdbc.update("UPDATE platform_operators SET factor_ciphertext=?,last_factor_counter=-1 WHERE user_id=?",
                encrypted, operator.userId());
        audit.append(operator.userId(), "FACTOR_ENROLLMENT_STARTED", operator.userId().toString(),
                "Operator verified their password and requested authenticator enrollment", correlation, "PENDING", "PENDING");
        String base32 = OperatorFactor.base32(secret);
        return new Enrollment(base32, "otpauth://totp/Chanter:" + operator.userId()
                + "?secret=" + base32 + "&issuer=Chanter&algorithm=SHA1&digits=6&period=30");
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Verified verify(String authorization, String password, String code, boolean confirmation, UUID correlation) {
        var operator = access.requireRole(authorization);
        FactorRow row = lock(operator.userId());
        access.requireRole(authorization);
        attempt(operator.userId(), row);
        requirePassword(operator.userId(), password);
        if (row.encrypted() == null || (!confirmation && !row.confirmed())) throw invalidCode();
        byte[] secret = factor.decrypt(operator.userId(), row.encrypted());
        long counter = matchingCounter(secret, code, row.lastCounter());
        if (counter < 0) throw invalidCode();
        jdbc.update("UPDATE platform_operators SET factor_confirmed=TRUE,last_factor_counter=?,factor_attempts=0 WHERE user_id=?",
                counter, operator.userId());
        jdbc.update("DELETE FROM platform_step_up WHERE user_id=? OR expires_at<=CURRENT_TIMESTAMP", operator.userId());
        byte[] opaque = new byte[32];
        random.nextBytes(opaque);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(opaque);
        Instant expires = Instant.now().plusSeconds(300);
        jdbc.update("INSERT INTO platform_step_up(token_hash,user_id,access_token_hash,expires_at) VALUES(?,?,?,?)",
                OperatorAccess.hash(token), operator.userId(), OperatorAccess.hash(authorization), expires.atOffset(ZoneOffset.UTC));
        audit.append(operator.userId(), confirmation ? "FACTOR_CONFIRMED" : "OPERATOR_VERIFIED", operator.userId().toString(),
                "Password and a new authenticator code verified", correlation, row.confirmed() ? "ENROLLED" : "PENDING", "ENROLLED");
        return new Verified(token, expires);
    }

    private FactorRow lock(UUID user) {
        return jdbc.queryForObject("""
                SELECT factor_ciphertext,factor_confirmed,last_factor_counter,factor_attempts,factor_window_started
                FROM platform_operators WHERE user_id=? FOR UPDATE
                """, (rs, row) -> new FactorRow(rs.getString(1), rs.getBoolean(2), rs.getLong(3), rs.getInt(4),
                rs.getObject(5, OffsetDateTime.class)), user);
    }

    private void attempt(UUID user, FactorRow row) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean newWindow = row.window() == null || row.window().plusMinutes(5).isBefore(now);
        if (!newWindow && row.attempts() >= 5)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait before trying operator verification again");
        jdbc.update("UPDATE platform_operators SET factor_attempts=?,factor_window_started=? WHERE user_id=?",
                newWindow ? 1 : row.attempts()+1, newWindow ? now : row.window(), user);
    }

    private void requirePassword(UUID user, String password) {
        var account = users.findById(user).orElseThrow(OperatorVerification::invalidCode);
        if (!account.emailVerified() || account.passwordHash() == null || password == null || password.length() > 128
                || !passwords.matches(password, account.passwordHash())) throw invalidCode();
    }

    private static long matchingCounter(byte[] secret, String code, long used) {
        if (code == null || !code.matches("[0-9]{6}")) return -1;
        long now = Instant.now().getEpochSecond()/30;
        for (long counter = now-1; counter <= now+1; counter++) {
            if (counter > used && MessageDigest.isEqual(code.getBytes(StandardCharsets.US_ASCII),
                    OperatorFactor.code(secret, counter, 6).getBytes(StandardCharsets.US_ASCII))) return counter;
        }
        return -1;
    }

    private static ResponseStatusException invalidCode() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Operator verification failed");
    }

    private record FactorRow(String encrypted, boolean confirmed, long lastCounter, int attempts, OffsetDateTime window) { }
    public record Enrollment(String secret, String authenticatorUri) { }
    public record Verified(String token, Instant expiresAt) { }
}
