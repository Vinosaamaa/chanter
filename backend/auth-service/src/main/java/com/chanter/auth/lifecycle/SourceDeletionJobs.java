package com.chanter.auth.lifecycle;

import com.chanter.common.events.*;
import com.chanter.common.lifecycle.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Fixed source authority enters the same canonical journal and durable delivery used by account deletion. */
public final class SourceDeletionJobs {
    private final JdbcTemplate jdbc;
    private final TerminalJournalStore journal;
    private final AuthTerminalRecovery auth;
    private final DurableOutbox outbox;
    private final DurableConsumer consumer;
    private final AccountDeletionProtocol protocol;
    public SourceDeletionJobs(JdbcTemplate jdbc,TransactionTemplate tx,TerminalJournalStore journal,AuthTerminalRecovery auth,
            DurableOutbox outbox,AccountDeletionProtocol protocol) {
        this.jdbc=jdbc; this.journal=journal; this.auth=auth; this.outbox=outbox; this.protocol=protocol;
        this.consumer=new DurableConsumer(jdbc,tx);
    }
    public void request(DurableEvent event) {
        var request=protocol.sourceRequest(event);
        journal.withAuthority(() -> consumer.apply(event,false,() -> {
            var existing=jdbc.query("SELECT id FROM lifecycle_source_deletions WHERE target_kind=? AND target_id=?",
                    (rs,n) -> rs.getObject(1,UUID.class),request.targetKind(),request.targetId());
            if(!existing.isEmpty()) {
                if(!existing.getFirst().equals(request.jobId()) || jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_source_deletions WHERE id=? AND requester_id=?",
                        Integer.class,request.jobId(),request.requesterId())!=1) throw invalid();
                return;
            }
            var entry=journal.append(request.targetKind(),request.targetId());
            var own=auth.applyCommitted(entry);
            jdbc.update("INSERT INTO lifecycle_source_deletions(id,requester_id,target_kind,target_id,terminal_revision,terminal_digest) VALUES (?,?,?,?,?,?)",
                    request.jobId(),request.requesterId(),entry.targetKind(),entry.targetId(),entry.revision(),entry.digest());
            for(String source:AccountExportJobs.SOURCES) {
                UUID command=source.equals("auth") ? null : outbox.append("lifecycle-"+source,AccountDeletionProtocol.TERMINAL,
                        AccountDeletionProtocol.key(entry.targetKind(),entry.targetId()),protocol.encode(new AccountDeletionProtocol.Terminal(request.jobId(),entry)));
                jdbc.update("INSERT INTO lifecycle_source_deletion_parts(job_id,source,state,event_id) VALUES (?,?,?,?)",
                        request.jobId(),source,source.equals("auth") ? own.name() : "PENDING",command);
            }
        }));
    }
    public void accept(DurableEvent event) {
        var receipt=protocol.receipt(event);
        if("ACCOUNT".equals(receipt.targetKind()) || "auth".equals(receipt.source()) || receipt.terminalRevision()==null) throw invalid();
        consumer.apply(event,false,() -> {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_source_deletions WHERE id=? AND target_kind=? AND target_id=? AND terminal_revision=? AND terminal_digest=?",
                    Integer.class,receipt.jobId(),receipt.targetKind(),receipt.targetId(),receipt.terminalRevision(),receipt.terminalDigest())!=1) throw invalid();
            String prior=jdbc.queryForObject("SELECT state FROM lifecycle_source_deletion_parts WHERE job_id=? AND source=?",String.class,receipt.jobId(),receipt.source());
            if(!"PENDING".equals(prior) && !prior.equals(receipt.state())) throw invalid();
            jdbc.update("UPDATE lifecycle_source_deletion_parts SET state=? WHERE job_id=? AND source=?",receipt.state(),receipt.jobId(),receipt.source());
        });
    }
    public Progress progress(UUID id) {
        var headers=jdbc.query("SELECT target_kind,target_id,terminal_revision,terminal_digest FROM lifecycle_source_deletions WHERE id=?",
                (rs,n) -> new Header(rs.getString(1),rs.getObject(2,UUID.class),rs.getLong(3),rs.getString(4)),id);
        if(headers.size()!=1) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"DELETION_NOT_FOUND");
        var header=headers.getFirst();
        var parts=jdbc.query("SELECT p.source,p.state,o.status FROM lifecycle_source_deletion_parts p LEFT JOIN durable_outbox o ON o.id=p.event_id WHERE p.job_id=? ORDER BY p.source",
                (rs,n) -> new AccountDeletionJobs.Part(rs.getString(1),rs.getString(2),"FAILED".equals(rs.getString(3)) ? "DELIVERY_FAILED" : null),id);
        boolean replicationPending=!journal.replicated(header.revision());
        boolean cleaned=parts.size()==7 && parts.stream().allMatch(part -> Set.of("COMPLETE","PRESERVED").contains(part.state()));
        return new Progress(id,header.kind(),header.target(),header.revision(),header.digest(),
                cleaned ? replicationPending ? "WAITING_FOR_REPLICA" : "COMPLETE" : "ERASING",replicationPending,List.copyOf(parts));
    }
    public PublicProgress progress(UUID id,UUID requester) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_source_deletions WHERE id=? AND requester_id=?",Integer.class,id,requester)!=1)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"DELETION_NOT_FOUND");
        var value=progress(id);
        return new PublicProgress(value.jobId(),value.targetKind(),value.targetId(),value.state(),value.replicationPending(),value.parts());
    }
    public record PublicProgress(UUID jobId,String targetKind,UUID targetId,String state,boolean replicationPending,List<AccountDeletionJobs.Part> parts) { }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid source deletion authority"); }
    private record Header(String kind,UUID target,long revision,String digest) { }
    public record Progress(UUID jobId,String targetKind,UUID targetId,long terminalRevision,String terminalDigest,
            String state,boolean replicationPending,List<AccountDeletionJobs.Part> parts) { }
}
