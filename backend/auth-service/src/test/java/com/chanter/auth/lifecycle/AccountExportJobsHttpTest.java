package com.chanter.auth.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.chanter.auth.application.AuthSessionService;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.JwtTokenService;
import com.chanter.common.events.DurableEvent;
import com.chanter.common.lifecycle.AccountExportProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties="spring.datasource.url=jdbc:h2:mem:account-export-jobs-http;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class AccountExportJobsHttpTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountExportJobs jobs;
    @Autowired AuthSessionService sessions;
    @Autowired JwtTokenService tokens;
    @Autowired AccountExportProtocol protocol;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired TerminalJournalStore journal;
    @Autowired com.chanter.auth.application.RefreshTokenRepository refreshTokens;
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Test void liveRecentAccountCreatesOnePrivateJobAndOnlyEverySourceReceiptMakesItReady() throws Exception {
        var owner = account(); var other = account(); UUID id = UUID.randomUUID();
        String authorization = bearer(owner); String path = "/api/v1/auth/account/exports";
        assertThat(send("POST", path, "{\"requestId\":\"" + id + "\"}", null).statusCode()).isEqualTo(401);
        var created = send("POST", path, "{\"requestId\":\"" + id + "\"}", authorization);
        assertThat(created.statusCode()).isEqualTo(202);
        var job = mapper.readValue(created.body(), AccountExportJobs.Job.class);
        assertThat(job.state()).isEqualTo("BUILDING");
        assertThat(job.parts()).hasSize(7);
        assertThat(send("POST", path, "{\"requestId\":\"" + id + "\"}", authorization).statusCode()).isEqualTo(202);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE aggregate_key=?", Integer.class, AccountExportProtocol.key(id))).isEqualTo(6);
        assertThat(send("GET", path + "/" + id, null, bearer(other)).statusCode()).isEqualTo(404);
        assertThat(send("POST", path, "{\"requestId\":\"" + id + "\"}", bearer(other)).statusCode()).isEqualTo(404);
        for (String source : AccountExportJobs.SOURCES) if (!source.equals("auth")) {
            var event = receipt(id, owner.user().id(), source, "READY", 1);
            jobs.accept(event); jobs.accept(event);
        }
        assertThat(jobs.requireDownload(authorization, id).state()).isEqualTo("READY");
        var changed = receipt(id, owner.user().id(), "community", "READY", 2);
        var changedReceipt = new AccountExportProtocol.Receipt(id, owner.user().id(), "community", "READY", "b".repeat(64));
        var changedEvent = new DurableEvent(changed.id(), 1, changed.producer(), changed.revision(), changed.kind(), changed.aggregateKey(), protocol.encode(changedReceipt));
        assertThatThrownBy(() -> jobs.accept(changedEvent)).hasMessageContaining("EXPORT_RECEIPT_CHANGED");
        var cancelled = jobs.cancel(authorization, id);
        assertThat(cancelled.state()).isEqualTo("CANCELLED");
        assertThat(cancelled.cleanupPending()).isTrue();
        assertThatThrownBy(() -> jobs.requireDownload(authorization, id)).hasMessageContaining("EXPORT_NOT_READY");
        for (String source : AccountExportJobs.SOURCES) if (!source.equals("auth")) jobs.accept(receipt(id, owner.user().id(), source, "CANCELLED", 3));
        assertThat(jobs.get(authorization, id).cleanupPending()).isFalse();
        jobs.accept(receipt(id, owner.user().id(), "community", "READY", 4));
        assertThat(jobs.get(authorization, id).state()).isEqualTo("CANCELLED");
        sessions.logout(owner.refreshToken());
        assertThat(send("GET", path + "/" + id, null, authorization).statusCode()).isEqualTo(401);
    }

    @Test void rotationDoesNotRenewRecentLoginAndLegacySessionlessTokensCannotExport() throws Exception {
        var owner = account(); String email = owner.user().email();
        jdbc.update("UPDATE auth_sessions SET created_at=? WHERE user_id=?", Timestamp.from(Instant.now().minusSeconds(301)), owner.user().id());
        var refreshed = sessions.refresh(owner.refreshToken());
        String path = "/api/v1/auth/account/exports";
        assertThat(send("POST", path, "{\"requestId\":\"" + UUID.randomUUID() + "\"}", bearer(refreshed)).statusCode()).isEqualTo(428);
        assertThat(send("GET", path, null, "Bearer " + tokens.createAccessToken(owner.user().id())).statusCode()).isEqualTo(401);
        var loggedIn = sessions.login(email, "private fixture password 251", "Newly authenticated browser");
        assertThat(send("POST", path, "{\"requestId\":\"" + UUID.randomUUID() + "\"}", bearer(loggedIn)).statusCode()).isEqualTo(202);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id=?", Integer.class, owner.user().id())).isEqualTo(2);
    }

    @Test void failureProgressIsHonestAndCancelledExportsAllowAnotherRequest() {
        var owner = account(); String authorization = bearer(owner);
        var first = jobs.create(authorization, UUID.randomUUID());
        jdbc.update("UPDATE durable_outbox SET status='FAILED',last_error='DELIVERY_FAILED' WHERE aggregate_key=? AND destination='lifecycle-media'", AccountExportProtocol.key(first.id()));
        var progress = jobs.get(authorization, first.id());
        assertThat(progress.state()).isEqualTo("BUILDING");
        assertThat(progress.parts().stream().filter(part -> part.source().equals("media")).findFirst().orElseThrow().errorCode()).isEqualTo("DELIVERY_FAILED");
        assertThatThrownBy(() -> jobs.create(authorization, UUID.randomUUID())).hasMessageContaining("EXPORT_ALREADY_ACTIVE");
        jobs.cancel(authorization, first.id());
        assertThat(jobs.create(authorization, UUID.randomUUID()).state()).isEqualTo("BUILDING");
    }

    @Test void terminalAccountClosureCancelsOnlyItsExportsAndCommitsWithTheOwningMutation() {
        var owner = account(); var other = account();
        var job = jobs.create(bearer(owner), UUID.randomUUID());
        var otherJob = jobs.create(bearer(other), UUID.randomUUID());
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThatThrownBy(() -> jobs.cancelAccount(owner.user().id())).isInstanceOf(IllegalStateException.class);
        tx.executeWithoutResult(status -> { jobs.cancelAccount(owner.user().id()); status.setRollbackOnly(); });
        assertThat(jobs.get(bearer(owner), job.id()).state()).isEqualTo("BUILDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_account_tombstones WHERE account_id=?", Integer.class, owner.user().id())).isZero();
        tx.executeWithoutResult(status -> jobs.cancelAccount(owner.user().id()));
        tx.executeWithoutResult(status -> jobs.cancelAccount(owner.user().id()));
        assertThat(jobs.get(bearer(owner), job.id()).state()).isEqualTo("CANCELLED");
        assertThat(jobs.get(bearer(owner), job.id()).cleanupPending()).isTrue();
        assertThat(jobs.get(bearer(other), otherJob.id()).state()).isEqualTo("BUILDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE aggregate_key=?", Integer.class, AccountExportProtocol.key(job.id()))).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_pages WHERE snapshot_id=?", Integer.class, job.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_pages WHERE snapshot_id=?", Integer.class, otherJob.id())).isPositive();
        assertThatThrownBy(() -> jobs.create(bearer(owner), UUID.randomUUID())).hasMessageContaining("EXPORT_ACCOUNT_DELETED");
        jobs.accept(receipt(job.id(), owner.user().id(), "media", "READY", 1));
        assertThat(jobs.get(bearer(owner), job.id()).state()).isEqualTo("CANCELLED");
    }

    @Test void accountTerminalAuthorityAndRevocationCannotBeBypassedByLoginOrRefresh() throws Exception {
        var owner = account(); var other = account();
        var job = jobs.create(bearer(owner), UUID.randomUUID());
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            journal.append("ACCOUNT", owner.user().id());
            refreshTokens.revokeAllForUser(owner.user().id(), Instant.now());
            jobs.cancelAccount(owner.user().id());
            status.setRollbackOnly();
        });
        assertThat(jobs.get(bearer(owner), job.id()).state()).isEqualTo("BUILDING");
        tx.executeWithoutResult(status -> {
            journal.append("ACCOUNT", owner.user().id());
            refreshTokens.revokeAllForUser(owner.user().id(), Instant.now());
            jobs.cancelAccount(owner.user().id());
        });
        assertThat(send("GET", "/api/v1/auth/account/exports/" + job.id(), null, bearer(owner)).statusCode()).isEqualTo(401);
        assertThatThrownBy(() -> sessions.refresh(owner.refreshToken())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> sessions.login(owner.user().email(), "private fixture password 251", "new browser"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id=? AND revoked_at IS NULL", Integer.class, owner.user().id())).isZero();
        assertThat(sessions.login(other.user().email(), "private fixture password 251", "other browser").user().id()).isEqualTo(other.user().id());
        // Revocation remains valid after a target becomes terminal.
        refreshTokens.revokeAllForUser(owner.user().id(), Instant.now());
    }

    @Test void sessionCreationWaitingOnAccountClosureCannotCreateAnActiveSessionAfterItCommits() throws Exception {
        var owner = account();
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var creating = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var closure = workers.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                journal.append("ACCOUNT", owner.user().id());
                refreshTokens.lockUser(owner.user().id());
                locked.countDown();
                try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                refreshTokens.revokeAllForUser(owner.user().id(), Instant.now());
                jobs.cancelAccount(owner.user().id());
            }));
            assertThat(locked.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var create = workers.submit(() -> {
                creating.countDown();
                refreshTokens.createSession(UUID.randomUUID(), owner.user().id(), UUID.randomUUID(), UUID.randomUUID().toString(),
                        Instant.now(), Instant.now().plusSeconds(3600), "new browser");
            });
            assertThat(creating.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> create.get(100, java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            closure.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThatThrownBy(() -> create.get(5, java.util.concurrent.TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id=? AND revoked_at IS NULL", Integer.class, owner.user().id())).isZero();
        } finally { release.countDown(); }
    }
    @Test void cancellationCannotBypassDailyWorkLimitAndOuterRollbackPublishesNoJobOrSnapshot() {
        var owner = account(); String authorization = bearer(owner); UUID rolledBack = UUID.randomUUID();
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            jobs.create(authorization, rolledBack); status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_export_jobs WHERE id=?", Integer.class, rolledBack)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_snapshots WHERE id=?", Integer.class, rolledBack)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE aggregate_key=?", Integer.class, AccountExportProtocol.key(rolledBack))).isZero();
        for (int count = 0; count < 5; count++) {
            var job = jobs.create(authorization, UUID.randomUUID()); jobs.cancel(authorization, job.id());
        }
        assertThatThrownBy(() -> jobs.create(authorization, UUID.randomUUID())).hasMessageContaining("EXPORT_DAILY_LIMIT");
    }
    private DurableEvent receipt(UUID job, UUID account, String source, String state, long revision) {
        var receipt = new AccountExportProtocol.Receipt(job, account, source, state, state.equals("READY") ? "a".repeat(64) : null);
        return new DurableEvent(UUID.randomUUID(), 1, source, revision, AccountExportProtocol.RECEIPT, AccountExportProtocol.key(job), protocol.encode(receipt));
    }
    private AuthSessionService.AuthSession account() {
        return sessions.registerWithStatus(UUID.randomUUID() + "@example.invalid", "private fixture password 251", "Export owner").session();
    }
    private static String bearer(AuthSessionService.AuthSession session) { return "Bearer " + session.accessToken(); }
    private HttpResponse<String> send(String method, String path, String body, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15));
        if (authorization != null) request.header("Authorization", authorization);
        request.header("Origin", "http://localhost:5173").header("X-Chanter-CSRF", "1");
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
