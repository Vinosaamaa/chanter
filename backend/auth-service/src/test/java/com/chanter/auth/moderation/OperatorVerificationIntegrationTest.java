package com.chanter.auth.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chanter.common.auth.JwtTokenService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = "chanter.moderation.operator-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@ActiveProfiles("test")
class OperatorVerificationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired OperatorVerification verification;
    @Autowired OperatorAccess access;
    @Autowired OperatorFactor factor;
    @Autowired JwtTokenService tokens;
    @Autowired PasswordEncoder passwords;

    @Test void secondFactorIsSingleUseAndRevocationInvalidatesAnIssuedStepUp() {
        UUID user = operator();
        String authorization = "Bearer " + tokens.createAccessToken(user);
        byte[] secret = factor.newSecret();
        jdbc.update("UPDATE platform_operators SET factor_ciphertext=?,factor_confirmed=TRUE WHERE user_id=?",
                factor.encrypt(user, secret), user);
        String code = OperatorFactor.code(secret, Instant.now().getEpochSecond() / 30, 6);
        var granted = verification.verify(authorization, "password123", code, false, UUID.randomUUID());
        assertThat(access.requireStepUp(authorization, granted.token()).userId()).isEqualTo(user);
        assertThatThrownBy(() -> verification.verify(authorization, "password123", code, false, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
        jdbc.update("UPDATE platform_operators SET revoked_at=? WHERE user_id=?", OffsetDateTime.now(), user);
        assertThatThrownBy(() -> access.requireStepUp(authorization, granted.token())).isInstanceOf(ResponseStatusException.class);
    }

    @Test void enrollmentRequiresPasswordAndNeverReturnsExistingConfirmedSecret() {
        UUID user = operator();
        String authorization = "Bearer " + tokens.createAccessToken(user);
        assertThatThrownBy(() -> verification.enroll(authorization, "wrong", UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
        var enrollment = verification.enroll(authorization, "password123", UUID.randomUUID());
        assertThat(enrollment.secret()).hasSize(32);
        String encrypted = jdbc.queryForObject("SELECT factor_ciphertext FROM platform_operators WHERE user_id=?", String.class, user);
        assertThat(encrypted).doesNotContain(enrollment.secret());
        byte[] secret = factor.decrypt(user, encrypted);
        verification.verify(authorization, "password123", OperatorFactor.code(secret, Instant.now().getEpochSecond()/30, 6), true, UUID.randomUUID());
        assertThatThrownBy(() -> verification.enroll(authorization, "password123", UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_audit WHERE actor_id=?", Integer.class, user)).isGreaterThanOrEqualTo(2);
    }

    private UUID operator() {
        UUID user = UUID.randomUUID();
        jdbc.update("INSERT INTO auth_users(id,email,password_hash,display_name,email_verified,created_at) VALUES(?,?,?,?,TRUE,CURRENT_TIMESTAMP)",
                user, user + "@operator.test", passwords.encode("password123"), "Operator");
        jdbc.update("INSERT INTO platform_operators(user_id,role,granted_at) VALUES(?,'ADMIN',CURRENT_TIMESTAMP)", user);
        return user;
    }
}
