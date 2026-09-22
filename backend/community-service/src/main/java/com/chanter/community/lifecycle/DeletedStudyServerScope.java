package com.chanter.community.lifecycle;

import com.chanter.common.lifecycle.TerminalJournal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Minimal dependent-source authority retained when the public community graph is erased. */
@Repository
public class DeletedStudyServerScope {
    public static final int MAX_PAGE=256;
    private final JdbcTemplate jdbc;
    public DeletedStudyServerScope(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    /** Called inside the terminal source transaction, before graph erasure and its receipt. */
    public boolean capture(TerminalJournal.Entry entry) {
        entry.validate();
        if(!"STUDY_SERVER".equals(entry.targetKind()) || !TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Study Server scope requires its terminal transaction");
        var prior=header(entry.targetId());
        if(!prior.isEmpty()) {
            requireMatch(prior.getFirst(),entry.revision(),entry.eventId(),entry.digest());
            return prior.getFirst().available();
        }
        boolean available=!jdbc.query("SELECT id FROM study_servers WHERE id=? FOR UPDATE",(rs,row)->rs.getObject(1,UUID.class),entry.targetId()).isEmpty();
        // PostgreSQL key-share checks for new children now wait until graph erasure commits.
        if(available) jdbc.query("SELECT id FROM courses WHERE study_server_id=? ORDER BY id FOR UPDATE",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Deleted scope capture interrupted");
                },entry.targetId());
        jdbc.update("INSERT INTO lifecycle_deleted_server_scopes VALUES (?,?,?,?,?)",entry.targetId(),entry.eventId(),entry.revision(),entry.digest(),available);
        if(available) {
            jdbc.update("INSERT INTO lifecycle_deleted_server_scope_ids SELECT study_server_id,'COURSE',id FROM courses WHERE study_server_id=?",entry.targetId());
            jdbc.update("INSERT INTO lifecycle_deleted_server_scope_ids SELECT study_server_id,'CHANNEL',id FROM study_server_channels WHERE study_server_id=?",entry.targetId());
            jdbc.update("""
                    INSERT INTO lifecycle_deleted_server_scope_ids
                    SELECT c.study_server_id,'CHANNEL',ch.id FROM course_channels ch JOIN courses c ON c.id=ch.course_id WHERE c.study_server_id=?
                    """,entry.targetId());
        }
        return available;
    }

    public Page page(UUID server,long revision,UUID event,String digest,String kind,UUID after,int limit) {
        TerminalJournal.requireTarget("STUDY_SERVER",server);
        if(revision<1 || event==null || digest==null || !digest.matches("[a-f0-9]{64}")
                || !List.of("COURSE","CHANNEL").contains(kind) || after==null || limit<1 || limit>MAX_PAGE)
            throw new IllegalArgumentException("Invalid deleted scope page");
        var headers=header(server);
        if(headers.size()!=1) throw unavailable();
        var header=headers.getFirst(); requireMatch(header,revision,event,digest);
        if(!header.available() || jdbc.queryForObject("""
                SELECT COUNT(*) FROM lifecycle_terminal_targets WHERE target_kind='STUDY_SERVER' AND target_id=? AND revision=? AND event_id=? AND digest=?
                """,Integer.class,server,revision,event,digest)!=1) throw unavailable();
        var rows=jdbc.query("SELECT scope_id FROM lifecycle_deleted_server_scope_ids WHERE study_server_id=? AND scope_kind=? AND scope_id>? ORDER BY scope_id LIMIT ?",
                (rs,row)->rs.getObject(1,UUID.class),server,kind,after,limit+1);
        boolean more=rows.size()>limit;
        var ids=List.copyOf(rows.subList(0,Math.min(limit,rows.size())));
        return new Page(1,server,revision,event,digest,kind,ids,more ? ids.getLast() : null);
    }

    private List<Header> header(UUID server) {
        return jdbc.query("SELECT event_id,revision,digest,available FROM lifecycle_deleted_server_scopes WHERE study_server_id=?",
                (rs,row)->new Header(rs.getObject(1,UUID.class),rs.getLong(2),rs.getString(3),rs.getBoolean(4)),server);
    }
    private static void requireMatch(Header header,long revision,UUID event,String digest) {
        if(header.revision()!=revision || !header.event().equals(event) || !header.digest().equals(digest))
            throw new IllegalArgumentException("Deleted scope authority does not match");
    }
    private static ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.CONFLICT,"DELETION_SCOPE_UNAVAILABLE"); }
    private record Header(UUID event,long revision,String digest,boolean available) { }
    public record Page(int schemaVersion,UUID studyServerId,long terminalRevision,UUID terminalEventId,String terminalDigest,
            String kind,List<UUID> ids,UUID nextAfter) { }
}
