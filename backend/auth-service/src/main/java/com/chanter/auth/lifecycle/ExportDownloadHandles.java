package com.chanter.auth.lifecycle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Short-lived navigation authorization; one hash per job, no bearer token in URLs or retained state. */
public final class ExportDownloadHandles {
    public static final String COOKIE = "chanter_export_download";
    public static final String SCHEMA = """
        CREATE TABLE lifecycle_export_downloads (
            job_id UUID PRIMARY KEY REFERENCES lifecycle_export_jobs(id) ON DELETE CASCADE,
            account_id UUID NOT NULL, session_id UUID NOT NULL,
            handle_hash VARCHAR(64) NOT NULL UNIQUE,
            expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
            access_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
            consumed_at TIMESTAMP WITH TIME ZONE
        )
        """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final LifecycleSessionAccess access;
    private final AccountExportJobs jobs;
    private final com.chanter.auth.application.RefreshTokenRepository refreshTokens;
    private final SecureRandom random = new SecureRandom();

    public ExportDownloadHandles(JdbcTemplate jdbc, TransactionTemplate tx, LifecycleSessionAccess access, AccountExportJobs jobs,
            com.chanter.auth.application.RefreshTokenRepository refreshTokens) {
        this.jdbc = jdbc; this.tx = tx; this.access = access; this.jobs = jobs; this.refreshTokens = refreshTokens;
    }

    public Issued issue(String authorization, UUID jobId, String browserRefreshCookie) {
        return tx.execute(status -> {
            var identity = access.requireIdentity(authorization, false);
            requireBrowserSession(identity.sessionId(), browserRefreshCookie);
            jobs.requireDownload(identity.userId(), identity.sessionId(), identity.expiresAt(), jobId);
            // The user/session locks serialize issuance; the job row also fences cancellation and competing issuance.
            jdbc.queryForObject("SELECT id FROM lifecycle_export_jobs WHERE id=? FOR UPDATE", UUID.class, jobId);
            var now = Instant.now();
            if (!identity.expiresAt().isAfter(now.plusSeconds(1))) throw inactive();
            var expires = identity.expiresAt().isBefore(now.plusSeconds(60)) ? identity.expiresAt() : now.plusSeconds(60);
            byte[] bytes = new byte[32]; random.nextBytes(bytes);
            String handle = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            jdbc.update("DELETE FROM lifecycle_export_downloads WHERE job_id=?", jobId);
            jdbc.update("INSERT INTO lifecycle_export_downloads VALUES (?,?,?,?,?,?,NULL)", jobId, identity.userId(), identity.sessionId(),
                    hash(handle), Timestamp.from(expires), Timestamp.from(identity.expiresAt()));
            return new Issued(handle, expires);
        });
    }

    public Grant consume(UUID jobId, String handle, String browserRefreshCookie) {
        if (handle == null || !handle.matches("[A-Za-z0-9_-]{43}")) throw inactive();
        return tx.execute(status -> {
            // Read scope first, then take the same user/session-before-job lock order as issuance and closure.
            var candidates = jdbc.query("SELECT account_id,session_id,access_expires_at FROM lifecycle_export_downloads WHERE job_id=? AND handle_hash=?",
                    (rs, row) -> new Grant(jobId, rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getTimestamp(3).toInstant()), jobId, hash(handle));
            if (candidates.size() != 1) throw inactive();
            var grant = candidates.getFirst();
            requireBrowserSession(grant.sessionId(), browserRefreshCookie);
            jobs.requireDownload(grant.accountId(), grant.sessionId(), grant.accessExpiresAt(), jobId);
            int claimed = jdbc.update("""
                UPDATE lifecycle_export_downloads SET consumed_at=?
                WHERE job_id=? AND handle_hash=? AND account_id=? AND session_id=? AND consumed_at IS NULL AND expires_at>?
                """, Timestamp.from(Instant.now()), jobId, hash(handle), grant.accountId(), grant.sessionId(), Timestamp.from(Instant.now()));
            if (claimed != 1) throw inactive();
            return grant;
        });
    }

    public static String path(UUID jobId) { return "/api/v1/auth/account/exports/" + jobId + "/download"; }
    private void requireBrowserSession(UUID session, String cookie) {
        if (cookie == null || cookie.length() > 512 || !refreshTokens.findSessionIdByTokenHash(hash(cookie)).filter(session::equals).isPresent())
            throw inactive();
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static ResponseStatusException inactive() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "EXPORT_DOWNLOAD_AUTHORIZATION_EXPIRED"); }
    public record Issued(String handle, Instant expiresAt) { }
    public record Grant(UUID jobId, UUID accountId, UUID sessionId, Instant accessExpiresAt) { }
}
