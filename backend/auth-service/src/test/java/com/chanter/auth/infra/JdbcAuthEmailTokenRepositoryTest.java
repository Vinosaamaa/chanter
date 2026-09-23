package com.chanter.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcAuthEmailTokenRepositoryTest {

    @ParameterizedTest
    @ValueSource(strings = {"EMAIL_VERIFY", "PASSWORD_RESET"})
    void concurrentRedemptionCanConsumeATokenOnlyOnce(String purpose) throws Exception {
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:token-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__auth_users.sql"),
                new ClassPathResource("db/migration/V2__production_auth.sql")).execute(datasource);
        var jdbc = new JdbcTemplate(datasource);
        jdbc.execute("CREATE TABLE lifecycle_terminal_journal(target_kind VARCHAR(32),target_id UUID)");
        var repository = new JdbcAuthEmailTokenRepository(jdbc);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO auth_users(id, email, password_hash, display_name) VALUES (?, ?, ?, ?)",
                userId, "learner@example.test", "unused", "Learner");
        transactions.executeWithoutResult(status -> repository.save(UUID.randomUUID(), userId, "test-token-hash", purpose, Instant.now().plusSeconds(3600)));
        var firstSelected = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.execute(transaction -> {
                var token = repository.findActiveByTokenHash("test-token-hash", purpose, Instant.now()).orElseThrow();
                firstSelected.countDown();
                await(releaseFirst);
                repository.markUsed(token.id(), Instant.now());
                return true;
            }));
            assertThat(firstSelected.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> transactions.execute(transaction -> {
                var token = repository.findActiveByTokenHash("test-token-hash", purpose, Instant.now());
                token.ifPresent(value -> repository.markUsed(value.id(), Instant.now()));
                return token.isPresent();
            }));
            try {
                second.get(250, TimeUnit.MILLISECONDS);
            } catch (TimeoutException expected) {
                // The second transaction waits for the first token consumer to commit.
            } finally {
                releaseFirst.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(5, TimeUnit.SECONDS)).isFalse();
        } finally {
            releaseFirst.countDown();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent token consumer");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Token consumer interrupted");
        }
    }
}
