package com.chanter.auth.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chanter.common.auth.JwtTokenService;
import com.chanter.auth.application.AuthSessionService;
import com.chanter.auth.application.AuthUserRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:operator-roles;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class OperatorRolesTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired OperatorRoles roles;
    @Autowired JwtTokenService tokens;
    @Autowired AuthSessionService sessions;
    @Autowired AuthUserRepository users;

    @Test void initialBootstrapIsAuditedAndCannotBeRepeated() {
        UUID first = user();
        UUID second = user();
        roles.bootstrap(first, "Initial operator enrollment", UUID.randomUUID());
        assertThat(jdbc.queryForObject("SELECT role FROM platform_operators WHERE user_id=?", String.class, first)).isEqualTo("ADMIN");
        assertThatThrownBy(() -> roles.bootstrap(second, "Attempt repeated bootstrap", UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_audit WHERE action='OPERATOR_BOOTSTRAPPED'", Integer.class)).isEqualTo(1);
    }

    @Test void reviewerCannotGrantEvenWithAValidStepUpAndAdminCannotRemoveThemself() {
        UUID reviewer = user();
        UUID admin = user();
        UUID target = user();
        String reviewerToken = grant(reviewer, "REVIEWER");
        String adminToken = grant(admin, "ADMIN");
        assertThatThrownBy(() -> roles.change(reviewerToken, "test-step-up"+reviewer, target, "ADMIN", "Attempt escalation", target.toString(), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> roles.change(adminToken, "test-step-up"+admin, admin, null, "Remove myself", admin.toString(), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
        roles.change(adminToken, "test-step-up"+admin, target, "REVIEWER", "Assigned operator duties", target.toString(), UUID.randomUUID());
        assertThat(jdbc.queryForObject("SELECT role FROM platform_operators WHERE user_id=?", String.class, target)).isEqualTo("REVIEWER");
        roles.change(adminToken, "test-step-up"+admin, target, null, "Operator duties ended", target.toString(), UUID.randomUUID());
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM platform_operators WHERE user_id=?", Boolean.class, target)).isTrue();
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth_users(id,email,password_hash,display_name,email_verified,created_at) VALUES(?,?,?,'Operator',TRUE,CURRENT_TIMESTAMP)",
                id, id+"@roles.test", "test-password-hash");
        return id;
    }

    private String grant(UUID user, String role) {
        jdbc.update("INSERT INTO platform_operators(user_id,role,granted_at) VALUES(?,?,CURRENT_TIMESTAMP)", user, role);
        String bearer = "Bearer "+sessions.issueSessionForUser(users.findById(user).orElseThrow()).accessToken();
        jdbc.update("INSERT INTO platform_step_up(token_hash,user_id,access_token_hash,expires_at) VALUES(?,?,?,?)",
                OperatorAccess.hash("test-step-up"+user), user, OperatorAccess.hash(bearer), OffsetDateTime.now().plusMinutes(3));
        // Each user's opaque value is distinct, as in production.
        return bearer;
    }
}
