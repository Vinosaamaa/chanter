package com.chanter.common.lifecycle;

import com.chanter.common.events.DurableOutbox;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Owning source preparation is durable access closure, never permission to erase before canonical authority. */
public final class SourceDeletionRequests {
    public static final String SCHEMA="""
        CREATE TABLE lifecycle_source_requests (
            target_id UUID PRIMARY KEY, job_id UUID NOT NULL UNIQUE, requester_id UUID NOT NULL,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, event_id UUID NOT NULL
        )
        """;
    private final String kind;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final DurableOutbox outbox;
    private final AccountDeletionProtocol protocol;
    public SourceDeletionRequests(String kind,JdbcTemplate jdbc,TransactionTemplate tx,DurableOutbox outbox,AccountDeletionProtocol protocol) {
        if(!"STUDY_SERVER".equals(kind) && !"RESOURCE".equals(kind)) throw new IllegalArgumentException("Unsupported source deletion kind");
        this.kind=kind; this.jdbc=jdbc; this.tx=tx; this.outbox=outbox; this.protocol=protocol;
    }
    /** The local authorization callback executes after the source head lock and before recording the request. */
    public Request request(UUID target,UUID requester,Runnable localAuthorization) {
        TerminalJournal.requireTarget(kind,target); TerminalJournal.requireTarget("ACCOUNT",requester);
        return tx.execute(status -> {
            jdbc.queryForObject("SELECT id FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE",Integer.class);
            var prior=jdbc.query("SELECT job_id,requester_id FROM lifecycle_source_requests WHERE target_id=?",
                    (rs,n) -> new Owner(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class)),target);
            if(!prior.isEmpty()) {
                if(!prior.getFirst().requester().equals(requester)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"DELETION_NOT_FOUND");
                return new Request(prior.getFirst().job(),target,"PENDING");
            }
            localAuthorization.run();
            if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_terminal_targets WHERE target_kind=? AND target_id=?",Integer.class,kind,target)>0)
                throw new ResponseStatusException(HttpStatus.GONE,"LIFECYCLE_TARGET_DELETED");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_source_requests r WHERE NOT EXISTS (SELECT 1 FROM lifecycle_terminal_targets t WHERE t.target_kind=? AND t.target_id=r.target_id)",
                    Integer.class,kind)>=128) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"DELETION_SOURCE_CAPACITY");
            UUID job=UUID.randomUUID();
            UUID event=outbox.append("lifecycle-auth",AccountDeletionProtocol.SOURCE_REQUEST,AccountDeletionProtocol.key(kind,target),
                    protocol.encode(new AccountDeletionProtocol.SourceRequest(job,kind,target,requester)));
            jdbc.update("INSERT INTO lifecycle_source_requests(target_id,job_id,requester_id,event_id) VALUES (?,?,?,?)",target,job,requester,event);
            return new Request(job,target,"PENDING");
        });
    }
    public boolean pending(UUID target) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_source_requests WHERE target_id=?",Integer.class,target)>0;
    }
    public void requireOpen(UUID target) {
        if(pending(target)) throw new ResponseStatusException(HttpStatus.GONE,"LIFECYCLE_DELETION_PENDING");
    }
    private record Owner(UUID job,UUID requester) { }
    public record Request(UUID jobId,UUID targetId,String state) { }
}
