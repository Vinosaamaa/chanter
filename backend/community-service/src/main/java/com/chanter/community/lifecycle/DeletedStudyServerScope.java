package com.chanter.community.lifecycle;

import com.chanter.common.lifecycle.TerminalJournal;
import com.chanter.common.lifecycle.DeletedScope;
import com.chanter.common.lifecycle.DeletedScopeStore;
import com.chanter.common.lifecycle.TerminalReapplyStore;
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
    private final boolean recovery;
    private final DeletedScopeStore imports;
    private final java.util.function.Supplier<TerminalReapplyStore> terminal;
    @org.springframework.beans.factory.annotation.Autowired
    public DeletedStudyServerScope(JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager transactions,
            @org.springframework.beans.factory.annotation.Value("${chanter.recovery-mode:false}") boolean recovery,
            org.springframework.beans.factory.ObjectProvider<TerminalReapplyStore> terminal) {
        this(jdbc,new org.springframework.transaction.support.TransactionTemplate(transactions),recovery,terminal::getObject);
    }
    DeletedStudyServerScope(JdbcTemplate jdbc,org.springframework.transaction.support.TransactionTemplate tx,boolean recovery,
            java.util.function.Supplier<TerminalReapplyStore> terminal) {
        this.jdbc=jdbc; this.recovery=recovery; this.terminal=terminal;
        tx.setTimeout(30);
        imports=new DeletedScopeStore(jdbc,tx,entry -> {
            // Only isolated recovery community may stage original archived scope before its own terminal replay.
            if(!recovery) terminal.get().cleanup(entry);
        },entry -> {
            if(terminal.get().terminal("STUDY_SERVER",entry.targetId())) terminal.get().reconcile(entry);
        });
    }

    public DeletedScope.Receipt importPage(DeletedScope.Import page) { return imports.accept(page); }

    /** Called inside the terminal source transaction, before graph erasure and its receipt. */
    public boolean capture(TerminalJournal.Entry entry) {
        entry.validate();
        if(!"STUDY_SERVER".equals(entry.targetKind()) || !TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Study Server scope requires its terminal transaction");
        if(imports.ready(entry,"COURSE") && imports.ready(entry,"CHANNEL")) {
            // Never lose the only remaining mapping for historical children absent from the current archived scope.
            return !Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM courses c WHERE c.study_server_id=? AND NOT EXISTS(
                        SELECT 1 FROM lifecycle_scope_import_ids s WHERE s.study_server_id=c.study_server_id AND s.scope_kind='COURSE' AND s.scope_id=c.id))
                    OR EXISTS(SELECT 1 FROM study_server_channels c WHERE c.study_server_id=? AND NOT EXISTS(
                        SELECT 1 FROM lifecycle_scope_import_ids s WHERE s.study_server_id=c.study_server_id AND s.scope_kind='CHANNEL' AND s.scope_id=c.id))
                    OR EXISTS(SELECT 1 FROM course_channels ch JOIN courses c ON c.id=ch.course_id WHERE c.study_server_id=? AND NOT EXISTS(
                        SELECT 1 FROM lifecycle_scope_import_ids s WHERE s.study_server_id=c.study_server_id AND s.scope_kind='CHANNEL' AND s.scope_id=ch.id))
                    """,Boolean.class,entry.targetId(),entry.targetId(),entry.targetId()));
        }
        // Restored graphs and even their saved scope may precede the current deletion. Only archived scope is authoritative here.
        if(recovery) return false;
        var prior=header(entry.targetId());
        if(!prior.isEmpty()) {
            requireMatch(prior.getFirst(),entry.revision(),entry.eventId(),entry.digest());
            return prior.getFirst().available();
        }
        boolean available=!jdbc.query("SELECT id FROM study_servers WHERE id=? FOR UPDATE",(rs,row)->rs.getObject(1,UUID.class),entry.targetId()).isEmpty();
        // PostgreSQL key-share checks for new children now wait until graph erasure commits.
        if(available) jdbc.query("SELECT id FROM courses WHERE study_server_id=? ORDER BY id FOR UPDATE",
                statement -> { statement.setObject(1,entry.targetId()); statement.setFetchSize(MAX_PAGE); },
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Deleted scope capture interrupted");
                });
        jdbc.update("INSERT INTO lifecycle_deleted_server_scopes VALUES (?,?,?,?,?)",entry.targetId(),entry.eventId(),entry.revision(),entry.digest(),available);
        if(available) {
            jdbc.update("INSERT INTO lifecycle_deleted_server_scope_ids SELECT study_server_id,'COURSE',id FROM courses WHERE study_server_id=?",entry.targetId());
            jdbc.update("INSERT INTO lifecycle_deleted_server_scope_ids SELECT study_server_id,'CHANNEL',id FROM study_server_channels WHERE study_server_id=?",entry.targetId());
            jdbc.update("""
                    INSERT INTO lifecycle_deleted_server_scope_ids
                    SELECT c.study_server_id,'CHANNEL',ch.id FROM course_channels ch JOIN courses c ON c.id=ch.course_id WHERE c.study_server_id=?
                    """,entry.targetId());
            for(String kind:List.of("COURSE","CHANNEL")) {
                long count=jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_deleted_server_scope_ids WHERE study_server_id=? AND scope_kind=?",Long.class,entry.targetId(),kind);
                String[] digest={DeletedScope.startDigest(entry.digest(),kind,count)};
                jdbc.query("SELECT scope_id FROM lifecycle_deleted_server_scope_ids WHERE study_server_id=? AND scope_kind=? ORDER BY scope_id",
                        statement -> { statement.setObject(1,entry.targetId()); statement.setString(2,kind); statement.setFetchSize(MAX_PAGE); },
                        (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                            if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Deleted scope capture interrupted");
                            digest[0]=DeletedScope.nextDigest(digest[0],rs.getObject(1,UUID.class));
                        });
                jdbc.update("INSERT INTO lifecycle_deleted_server_scope_digests VALUES (?,?,?,?)",entry.targetId(),kind,count,digest[0]);
            }
        }
        return available;
    }

    public DeletedScope.Page page(UUID server,long revision,UUID event,String digest,String kind,UUID after,int limit) {
        TerminalJournal.requireTarget("STUDY_SERVER",server);
        if(revision<1 || event==null || digest==null || !digest.matches("[a-f0-9]{64}")
                || !List.of("COURSE","CHANNEL").contains(kind) || after==null || limit<1 || limit>MAX_PAGE)
            throw new IllegalArgumentException("Invalid deleted scope page");
        if(jdbc.queryForObject("""
                SELECT COUNT(*) FROM lifecycle_terminal_targets WHERE target_kind='STUDY_SERVER' AND target_id=? AND revision=? AND event_id=? AND digest=?
                """,Integer.class,server,revision,event,digest)!=1) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_terminal_targets WHERE target_kind='STUDY_SERVER' AND target_id=?",Integer.class,server)!=0)
                throw new IllegalArgumentException("Deleted scope authority does not match");
            throw unavailable();
        }
        boolean imported=jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_scope_imports WHERE study_server_id=? AND scope_kind=? AND ready=TRUE",Integer.class,server,kind)==1;
        if(imported) return imports.page(server,revision,event,digest,kind,after,limit);
        if(recovery) throw unavailable();
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
        var result=jdbc.queryForObject("SELECT total_count,scope_digest FROM lifecycle_deleted_server_scope_digests WHERE study_server_id=? AND scope_kind=?",
                (rs,row)->new DeletedScope.Page(1,server,revision,event,digest,kind,after,rs.getLong(1),rs.getString(2),ids,more ? ids.getLast() : null),server,kind);
        result.validate(); return result;
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
}
