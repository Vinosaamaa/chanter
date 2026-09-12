package com.chanter.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mail.MailSendException;
import org.springframework.boot.health.contributor.Status;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.LoggerFactory;

class JdbcEmailOutboxTest {

    private final Instant now = Instant.parse("2026-09-11T12:00:00Z");
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private SimpleMeterRegistry metrics;
    private SmtpEmailTransport transport;

    @BeforeEach
    void setUp() {
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:email-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V4__transactional_email_outbox.sql"))
                .execute(datasource);
        jdbc = new JdbcTemplate(datasource);
        transactions = new DataSourceTransactionManager(datasource);
        metrics = new SimpleMeterRegistry();
        transport = mock(SmtpEmailTransport.class);
    }

    @Test
    void concurrentWorkersSkipTheMessageAlreadyBeingDelivered() throws Exception {
        var sending = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            sending.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for SMTP worker");
            }
            return null;
        }).when(transport).send(anyString(), anyString(), anyString());
        var first = outbox(now, transport);
        first.send("learner@example.test", "Verify email", "private token");
        var otherTransport = mock(SmtpEmailTransport.class);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var delivery = executor.submit(first::deliverNext);
            assertThat(sending.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(executor.submit(outbox(now, otherTransport)::deliverNext).get(2, TimeUnit.SECONDS)).isFalse();
            } finally {
                release.countDown();
            }
            assertThat(delivery.get(5, TimeUnit.SECONDS)).isTrue();
            verifyNoInteractions(otherTransport);
        } finally {
            release.countDown();
        }
    }

    @Test
    void removesCompletedMetadataAfterSevenDays() {
        var worker = outbox(now, transport);
        worker.send("learner@example.test", "Verify email", "private token");
        worker.deliverNext();
        outbox(now.plus(Duration.ofDays(8)), transport).purgeCompletedMetadata();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_email_outbox", Integer.class)).isZero();
    }

    @Test
    void deliveryHealthReportsRetriesAndRecoversAfterSuccessfulDelivery() {
        var failing = mock(SmtpEmailTransport.class);
        doThrow(new MailSendException("private provider response")).when(failing).send(anyString(), anyString(), anyString());
        JdbcEmailOutbox worker = outbox(now, failing);
        worker.send("learner@example.test", "Verify email", "private token");
        worker.deliverNext();

        var health = new EmailDeliveryHealthIndicator(worker).health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("pending", 1L).containsEntry("retrying", 1L);
        assertThat(health.getDetails().toString()).doesNotContain("private", "learner");

        var recovered = outbox(now.plusSeconds(30), transport);
        recovered.deliverNext();
        assertThat(new EmailDeliveryHealthIndicator(recovered).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void expiresAndClearsUnsentSecretsWithoutCallingTheProvider() {
        outbox(now, transport).send("learner@example.test", "Reset password", "private token", now.plusSeconds(20));

        assertThat(outbox(now.plusSeconds(20), transport).deliverNext()).isTrue();

        verifyNoInteractions(transport);
        assertThat(jdbc.queryForMap("SELECT status, recipient, subject, body_text FROM auth_email_outbox"))
                .containsEntry("status", "EXPIRED").containsEntry("recipient", null)
                .containsEntry("subject", null).containsEntry("body_text", null);
        assertThat(metrics.get("chanter.auth.email.delivery").tag("outcome", "expired").counter().count()).isEqualTo(1);
    }

    @Test
    void enqueueRollsBackWithTheCallingAuthenticationTransaction() {
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            outbox(now, transport).send("learner@example.test", "Verify email", "private token");
            transaction.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_email_outbox", Integer.class)).isZero();
        assertThat(outbox(now, transport).deliverNext()).isFalse();
        verifyNoInteractions(transport);
    }

    @Test
    void failurePersistsBackoffAndRetriesAfterRestartWithoutLeakingProviderDetails() {
        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        Logger logger = (Logger) LoggerFactory.getLogger(JdbcEmailOutbox.class);
        logger.addAppender(logs);
        try {
            var failing = mock(SmtpEmailTransport.class);
            doThrow(new MailSendException("secret-token recipient@example.test smtp-private.invalid"))
                    .when(failing).send(anyString(), anyString(), anyString());
            JdbcEmailOutbox worker = outbox(now, failing);
            worker.send("learner@example.test", "Reset password", "secret-token", now.plusSeconds(3600));

            assertThat(worker.deliverNext()).isTrue();
            assertThat(jdbc.queryForMap("SELECT status, attempts, next_attempt_at FROM auth_email_outbox"))
                    .containsEntry("status", "PENDING").containsEntry("attempts", 1);
            assertThat(metrics.get("chanter.auth.email.delivery").tag("outcome", "retry").counter().count()).isEqualTo(1);
            assertThat(outbox(now.plusSeconds(29), transport).deliverNext()).isFalse();
            assertThat(outbox(now.plusSeconds(30), transport).deliverNext()).isTrue();
            verify(transport).send("learner@example.test", "Reset password", "secret-token");
            assertThat(logs.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("secret-token", "recipient@example.test", "smtp-private.invalid", "learner@example.test");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @Test
    void committedMailSurvivesWorkerRestartAndClearsPayloadAfterProviderAcceptsIt() {
        JdbcEmailOutbox firstProcess = outbox(now, transport);
        firstProcess.send("learner@example.test", "Verify your Chanter email", "private test token", now.plusSeconds(3600));
        verifyNoInteractions(transport);

        JdbcEmailOutbox restartedProcess = outbox(now, transport);
        assertThat(restartedProcess.deliverNext()).isTrue();
        assertThat(restartedProcess.deliverNext()).isFalse();

        verify(transport).send("learner@example.test", "Verify your Chanter email", "private test token");
        assertThat(jdbc.queryForMap("SELECT status, recipient, subject, body_text FROM auth_email_outbox"))
                .containsEntry("status", "DELIVERED")
                .containsEntry("recipient", null).containsEntry("subject", null).containsEntry("body_text", null);
        assertThat(metrics.get("chanter.auth.email.delivery").tag("outcome", "accepted").counter().count()).isEqualTo(1);
    }

    private JdbcEmailOutbox outbox(Instant time, SmtpEmailTransport sender) {
        return new JdbcEmailOutbox(jdbc, transactions, sender, metrics, Duration.ofHours(24),
                Clock.fixed(time, ZoneOffset.UTC));
    }
}
