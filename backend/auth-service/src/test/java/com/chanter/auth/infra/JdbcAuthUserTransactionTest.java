package com.chanter.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chanter.auth.application.AuthUserRepository;
import com.chanter.auth.domain.AuthUser;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class JdbcAuthUserTransactionTest {
    @Autowired AuthUserRepository users;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void duplicateInsertCanBeRecoveredAndTheOuterAccountTransactionStillRollsBack() {
        var existing = user("existing-" + UUID.randomUUID() + "@example.test");
        users.save(existing);
        var provisional = user("provisional-" + UUID.randomUUID() + "@example.test");
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            users.save(provisional);
            assertThatThrownBy(() -> users.save(user(existing.email())))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(users.findByEmail(existing.email())).isPresent();
            users.markEmailVerified(provisional.id());
            transaction.setRollbackOnly();
        });
        assertThat(users.findById(provisional.id())).isEmpty();
        assertThat(users.findById(existing.id())).isPresent();
    }

    private static AuthUser user(String email) {
        return new AuthUser(UUID.randomUUID(), email, "unused-test-hash", "Learner", false, Instant.now());
    }
}
