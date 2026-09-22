package com.chanter.auth.lifecycle;

import com.chanter.common.events.*;
import com.chanter.common.lifecycle.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Auth coordinates authority and progress; the existing outbox owns all durable delivery. */
public final class AccountDeletionJobs {
    public static final Duration RECEIPT_RETENTION=Duration.ofDays(7);
    public static final String COOKIE="__Secure-chanter-deletion-receipt";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final LifecycleSessionAccess access;
    private final TerminalJournalStore journal;
    private final AuthTerminalRecovery auth;
    private final DurableOutbox outbox;
    private final DurableConsumer consumer;
    private final AccountDeletionProtocol protocol;
    private final Clock clock;
    private final java.security.SecureRandom random=new java.security.SecureRandom();
    public AccountDeletionJobs(JdbcTemplate jdbc,TransactionTemplate tx,LifecycleSessionAccess access,TerminalJournalStore journal,
            AuthTerminalRecovery auth,DurableOutbox outbox,AccountDeletionProtocol protocol,Clock clock) {
        this.jdbc=jdbc; this.tx=tx; this.access=access; this.journal=journal; this.auth=auth;
        this.outbox=outbox; this.consumer=new DurableConsumer(jdbc,tx); this.protocol=protocol; this.clock=clock;
    }
    public Prepared prepare(String authorization,UUID id) {
        requireId(id);
        return tx.execute(status -> {
            UUID account=access.require(authorization,true); lock();
            if(!headers(id).isEmpty()) {
                Header old=require(id,account);
                if(!Set.of("PREPARING","PREPARED","BLOCKED_OWNERSHIP").contains(old.state())) throw rejected("DELETION_PREPARATION_CLOSED");
            } else {
                if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_deletion_jobs WHERE account_id=? AND state<>'CANCELLED'",Integer.class,account)>0)
                    throw rejected("DELETION_ALREADY_ACTIVE");
                if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_deletion_jobs WHERE account_id=? AND created_at>?",Integer.class,account,time(clock.instant().minus(Duration.ofDays(1))))>=5)
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"DELETION_DAILY_LIMIT");
                if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_deletion_jobs WHERE state IN ('PREPARING','PREPARED','BLOCKED_OWNERSHIP','CANCELLING')",Integer.class)>=16)
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"DELETION_PREPARATION_CAPACITY");
                Instant now=clock.instant();
                UUID command=outbox.append("lifecycle-community",AccountDeletionProtocol.PREPARE,AccountDeletionProtocol.key("ACCOUNT",account),
                        protocol.encode(new AccountDeletionProtocol.Preparation(id,account)));
                jdbc.update("INSERT INTO lifecycle_deletion_jobs(id,account_id,state,created_at,expires_at,prepare_event_id) VALUES (?,?,'PREPARING',?,?,?)",
                        id,account,time(now),time(now.plus(Duration.ofMinutes(30))),command);
            }
            byte[] bytes=new byte[32]; random.nextBytes(bytes);
            String handle=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            jdbc.update("UPDATE lifecycle_deletion_jobs SET receipt_hash=?,receipt_expires_at=? WHERE id=?",hash(handle),time(clock.instant().plus(RECEIPT_RETENTION)),id);
            return new Prepared(read(id),handle);
        });
    }
    public Job get(String authorization,UUID id) {
        return tx.execute(status -> { require(id,access.require(authorization,false)); return read(id); });
    }
    public Job receipt(UUID id,String handle) {
        requireId(id);
        if(handle==null || !handle.matches("[A-Za-z0-9_-]{43}")) throw missing();
        return tx.execute(status -> {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_deletion_jobs WHERE id=? AND receipt_hash=? AND receipt_expires_at>?",
                    Integer.class,id,hash(handle),time(clock.instant()))!=1) throw missing();
            return read(id);
        });
    }
    public Job confirm(String authorization,UUID id) {
        return journal.withAuthority(() -> {
            UUID account=access.require(authorization,true); lock(); Header job=require(id,account);
            if(!"PREPARED".equals(job.state()) || !job.expiresAt().isAfter(clock.instant())) throw rejected("DELETION_NOT_PREPARED");
            var entry=journal.append("ACCOUNT",account);
            var own=auth.applyCommitted(entry);
            jdbc.update("UPDATE lifecycle_deletion_jobs SET state='ERASING',terminal_revision=?,terminal_digest=? WHERE id=?",entry.revision(),entry.digest(),id);
            for(String source:AccountExportJobs.SOURCES) {
                UUID event=source.equals("auth") ? null : outbox.append("lifecycle-"+source,AccountDeletionProtocol.TERMINAL,
                        AccountDeletionProtocol.key("ACCOUNT",account),protocol.encode(new AccountDeletionProtocol.Terminal(id,entry)));
                jdbc.update("INSERT INTO lifecycle_deletion_parts(job_id,source,state,event_id) VALUES (?,?,?,?)",id,source,source.equals("auth") ? own.name() : "PENDING",event);
            }
            return read(id);
        });
    }
    public Job cancel(String authorization,UUID id) {
        return tx.execute(status -> { UUID account=access.require(authorization,false); lock(); Header job=require(id,account); cancel(job); return read(id); });
    }
    private void cancel(Header job) {
        if(Set.of("CANCELLED","CANCELLING").contains(job.state())) return;
        if("ERASING".equals(job.state())) throw rejected("DELETION_IRREVERSIBLE");
        UUID event=outbox.append("lifecycle-community",AccountDeletionProtocol.RELEASE,AccountDeletionProtocol.key("ACCOUNT",job.account()),
                protocol.encode(new AccountDeletionProtocol.Preparation(job.id(),job.account())));
        jdbc.update("UPDATE lifecycle_deletion_jobs SET state='CANCELLING',prepare_event_id=? WHERE id=?",event,job.id());
    }
    public void expirePreparations() {
        tx.executeWithoutResult(status -> {
            lock();
            var ids=jdbc.query("SELECT id FROM lifecycle_deletion_jobs WHERE state IN ('PREPARING','PREPARED','BLOCKED_OWNERSHIP') AND expires_at<=? ORDER BY expires_at LIMIT 16",
                    (rs,n) -> rs.getObject(1,UUID.class),time(clock.instant()));
            for(UUID id:ids) cancel(headers(id).getFirst());
            jdbc.update("UPDATE lifecycle_deletion_jobs SET receipt_hash=NULL WHERE receipt_expires_at<=?",time(clock.instant()));
            jdbc.update("DELETE FROM lifecycle_deletion_jobs WHERE state='CANCELLED' AND created_at<?",time(clock.instant().minus(Duration.ofDays(30))));
        });
    }
    public void accept(DurableEvent event) {
        var receipt=protocol.receipt(event);
        if(receipt.source().equals("auth") || !receipt.targetKind().equals("ACCOUNT")) throw new IllegalArgumentException("Invalid account deletion receipt");
        consumer.apply(event,false,() -> {
            lock(); Header job=require(receipt.jobId(),receipt.targetId());
            if(receipt.terminalRevision()==null) {
                if(Set.of("CANCELLED","ERASING").contains(job.state())) return;
                if("CANCELLING".equals(job.state())) {
                    if("RELEASED".equals(receipt.state())) jdbc.update("UPDATE lifecycle_deletion_jobs SET state='CANCELLED' WHERE id=?",job.id());
                    return;
                }
                if(!Set.of("PREPARED","BLOCKED_OWNERSHIP").contains(receipt.state())) throw rejected("DELETION_RECEIPT_STATE_MISMATCH");
                jdbc.update("UPDATE lifecycle_deletion_jobs SET state=? WHERE id=?",receipt.state(),job.id());
            } else {
                if(!"ERASING".equals(job.state()) || !receipt.terminalRevision().equals(job.revision()) || !receipt.terminalDigest().equals(job.digest()))
                    throw rejected("DELETION_RECEIPT_AUTHORITY_MISMATCH");
                String prior=jdbc.queryForObject("SELECT state FROM lifecycle_deletion_parts WHERE job_id=? AND source=?",String.class,job.id(),receipt.source());
                if(!"PENDING".equals(prior) && !prior.equals(receipt.state())) throw rejected("DELETION_RECEIPT_CHANGED");
                jdbc.update("UPDATE lifecycle_deletion_parts SET state=? WHERE job_id=? AND source=?",receipt.state(),job.id(),receipt.source());
            }
        });
    }
    private Job read(UUID id) {
        Header header=headers(id).stream().findFirst().orElseThrow(AccountDeletionJobs::missing);
        var parts=jdbc.query("SELECT p.source,p.state,o.status FROM lifecycle_deletion_parts p LEFT JOIN durable_outbox o ON o.id=p.event_id WHERE p.job_id=? ORDER BY p.source",
                (rs,n) -> new Part(rs.getString(1),rs.getString(2),"FAILED".equals(rs.getString(3)) ? "DELIVERY_FAILED" : null),id);
        boolean replication=header.revision()!=null && !journal.replicated(header.revision());
        String state=header.state();
        if(Set.of("PREPARING","PREPARED","BLOCKED_OWNERSHIP").contains(state) && !header.expiresAt().isAfter(clock.instant()))
            state="PREPARATION_EXPIRED";
        if("ERASING".equals(state) && parts.size()==7 && parts.stream().allMatch(part -> Set.of("COMPLETE","PRESERVED").contains(part.state())))
            state=replication ? "WAITING_FOR_REPLICA" : "COMPLETE";
        String prepareError=Set.of("PREPARING","CANCELLING").contains(header.state())
                && jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE id=? AND status='FAILED'",Integer.class,header.prepareEvent())>0 ? "DELIVERY_FAILED" : null;
        return new Job(id,state,header.createdAt(),header.expiresAt(),replication,prepareError,List.copyOf(parts));
    }
    private Header require(UUID id,UUID account) {
        var rows=headers(id); if(rows.size()!=1 || !rows.getFirst().account().equals(account)) throw missing(); return rows.getFirst();
    }
    private List<Header> headers(UUID id) {
        return jdbc.query("SELECT * FROM lifecycle_deletion_jobs WHERE id=?",(rs,n) -> new Header(rs.getObject("id",UUID.class),rs.getObject("account_id",UUID.class),
                rs.getString("state"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("expires_at").toInstant(),
                rs.getObject("terminal_revision",Long.class),rs.getString("terminal_digest"),rs.getObject("prepare_event_id",UUID.class)),id);
    }
    private void lock() { jdbc.queryForObject("SELECT id FROM lifecycle_deletion_lock WHERE id=1 FOR UPDATE",Integer.class); }
    private static String hash(String handle) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(handle.getBytes(java.nio.charset.StandardCharsets.US_ASCII))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static String path(UUID id) { return "/api/v1/auth/account/deletions/"+id+"/receipt"; }
    private static void requireId(UUID id) { if(id==null || id.equals(new UUID(0,0))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_DELETION_REQUEST"); }
    private static Timestamp time(Instant instant) { return Timestamp.from(instant); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"DELETION_NOT_FOUND"); }
    private static ResponseStatusException rejected(String code) { return new ResponseStatusException(HttpStatus.CONFLICT,code); }
    private record Header(UUID id,UUID account,String state,Instant createdAt,Instant expiresAt,Long revision,String digest,UUID prepareEvent) { }
    public record Part(String source,String state,String errorCode) { }
    public record Job(UUID id,String state,Instant createdAt,Instant preparationExpiresAt,boolean replicationPending,String preparationError,List<Part> parts) { }
    public record Prepared(Job job,String handle) { }
}
