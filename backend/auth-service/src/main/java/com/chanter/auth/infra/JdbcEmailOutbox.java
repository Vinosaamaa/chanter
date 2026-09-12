package com.chanter.auth.infra;

import com.chanter.auth.application.EmailSender;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Repository
public class JdbcEmailOutbox implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(JdbcEmailOutbox.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final SmtpEmailTransport transport;
    private final MeterRegistry metrics;
    private final Duration maximumAge;
    private final Clock clock;

    @Autowired
    public JdbcEmailOutbox(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                           SmtpEmailTransport transport, MeterRegistry metrics,
                           @Value("${chanter.auth.email-token-ttl:24h}") Duration maximumAge) {
        this(jdbc, transactionManager, transport, metrics, maximumAge, Clock.systemUTC());
    }

    JdbcEmailOutbox(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                    SmtpEmailTransport transport, MeterRegistry metrics, Duration maximumAge, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transport = transport;
        this.metrics = metrics;
        this.maximumAge = maximumAge;
        this.clock = clock;
        if (maximumAge.isNegative() || maximumAge.isZero()) {
            throw new IllegalStateException("Email token lifetime must be positive");
        }
    }

    @Override
    public void send(String recipient, String subject, String body) {
        send(recipient, subject, body, clock.instant().plus(maximumAge));
    }

    @Override
    public void send(String recipient, String subject, String body, Instant expiresAt) {
        if (recipient == null || recipient.isBlank() || recipient.length() > 320 || recipient.contains("\r")
                || recipient.contains("\n") || subject == null || subject.isBlank() || subject.length() > 256
                || subject.contains("\r") || subject.contains("\n") || body == null || body.isBlank()) {
            throw new IllegalArgumentException("Transactional email requires a valid recipient, subject and body");
        }
        Instant now = clock.instant();
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("Transactional email must expire in the future");
        }
        Instant deadline = expiresAt.isBefore(now.plus(maximumAge)) ? expiresAt : now.plus(maximumAge);
        // JdbcTemplate joins the caller's transaction, so token creation and enqueue commit together.
        jdbc.update("""
                INSERT INTO auth_email_outbox (id, recipient, subject, body_text, created_at, expires_at, next_attempt_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), recipient, subject, body, Timestamp.from(now), Timestamp.from(deadline), Timestamp.from(now));
    }

    /** One short, bounded SMTP attempt per row lock. A crash leaves the row available after rollback. */
    public boolean deliverNext() {
        return Boolean.TRUE.equals(transactions.execute(transaction -> {
            Instant now = clock.instant();
            var next = jdbc.query("""
                    SELECT id, recipient, subject, body_text, attempts, expires_at
                    FROM auth_email_outbox
                    WHERE status = 'PENDING' AND next_attempt_at <= ?
                    ORDER BY next_attempt_at, created_at
                    LIMIT 1 FOR UPDATE SKIP LOCKED
                    """, (row, index) -> new QueuedEmail(row.getObject("id", UUID.class), row.getString("recipient"),
                    row.getString("subject"), row.getString("body_text"), row.getInt("attempts"),
                    row.getTimestamp("expires_at").toInstant()), Timestamp.from(now)).stream().findFirst();
            if (next.isEmpty()) {
                return false;
            }
            QueuedEmail email = next.orElseThrow();
            if (!email.expiresAt().isAfter(clock.instant())) {
                complete(email.id(), "EXPIRED", 0);
                metrics.counter("chanter.auth.email.delivery", "outcome", "expired").increment();
                log.warn("Auth email expired before delivery messageId={}", email.id());
                return true;
            }
            try {
                transport.send(email.recipient(), email.subject(), email.body());
            } catch (MailException exception) {
                // Never retain exception messages: SMTP failures can include credentials, recipients and bodies.
                long delaySeconds = Math.min(900L, 30L << Math.min(email.attempts(), 5));
                Instant nextAttempt = clock.instant().plusSeconds(delaySeconds);
                if (nextAttempt.isAfter(email.expiresAt())) {
                    nextAttempt = email.expiresAt();
                }
                jdbc.update("UPDATE auth_email_outbox SET attempts = attempts + 1, next_attempt_at = ? WHERE id = ?",
                        Timestamp.from(nextAttempt), email.id());
                metrics.counter("chanter.auth.email.delivery", "outcome", "retry").increment();
                log.warn("Auth email retry scheduled messageId={} attempt={}", email.id(), email.attempts() + 1);
                return true;
            }
            complete(email.id(), "DELIVERED", 1);
            metrics.counter("chanter.auth.email.delivery", "outcome", "accepted").increment();
            return true;
        }));
    }

    private void complete(UUID id, String status, int attempts) {
        jdbc.update("""
                UPDATE auth_email_outbox
                SET status = ?, recipient = NULL, subject = NULL, body_text = NULL,
                    attempts = attempts + ?, completed_at = ?
                WHERE id = ?
                """, status, attempts, Timestamp.from(clock.instant()), id);
    }

    public DeliveryStatus deliveryStatus() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS pending,
                       COUNT(CASE WHEN attempts > 0 THEN 1 END) AS retrying,
                       MIN(created_at) AS oldest
                FROM auth_email_outbox WHERE status = 'PENDING'
                """, (row, index) -> new DeliveryStatus(row.getLong("pending"), row.getLong("retrying"),
                row.getTimestamp("oldest") == null ? 0L
                        : Math.max(0L, Duration.between(row.getTimestamp("oldest").toInstant(), clock.instant()).toSeconds())));
    }

    public void purgeCompletedMetadata() {
        jdbc.update("DELETE FROM auth_email_outbox WHERE status <> 'PENDING' AND completed_at < ?",
                Timestamp.from(clock.instant().minus(Duration.ofDays(7))));
    }

    public record DeliveryStatus(long pending, long retrying, long oldestPendingSeconds) {
    }

    private record QueuedEmail(UUID id, String recipient, String subject, String body, int attempts, Instant expiresAt) {
        @Override
        public String toString() {
            return "QueuedEmail[redacted]";
        }
    }
}
