package com.chanter.community.lifecycle;

import com.chanter.common.lifecycle.SourceDeletionRequests;
import com.chanter.common.lifecycle.TerminalReapplyStore;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Owning mutations take terminal authority before graph/content locks and keep it through commit. */
@Component
public final class CommunityLifecycleWrites {
    private final TerminalReapplyStore terminal;
    private final SourceDeletionRequests pending;
    private final JdbcTemplate jdbc;
    public CommunityLifecycleWrites(TerminalReapplyStore terminal,SourceDeletionRequests pending,JdbcTemplate jdbc) {
        this.terminal=terminal; this.pending=pending; this.jdbc=jdbc;
    }
    public void accounts(UUID... accounts) {
        for(UUID account:accounts) if(account!=null) terminal.requireWritable("ACCOUNT",account);
    }
    public void server(UUID server) {
        terminal.requireWritable("STUDY_SERVER",server); pending.requireOpen(server);
        jdbc.query("SELECT owner_user_id FROM study_servers WHERE id=?",(rs,row)->rs.getObject(1,UUID.class),server)
                .forEach(owner -> terminal.requireWritable("ACCOUNT",owner));
    }
    public void course(UUID id) { server(resolve("SELECT study_server_id FROM courses WHERE id=?",id)); }
    public void cohort(UUID id) { server(resolve("SELECT c.study_server_id FROM cohorts h JOIN courses c ON c.id=h.course_id WHERE h.id=?",id)); }
    public void session(UUID id) { server(resolve("SELECT c.study_server_id FROM office_hours_sessions s JOIN cohorts h ON h.id=s.cohort_id JOIN courses c ON c.id=h.course_id WHERE s.id=?",id)); }
    public void courseChannel(UUID id) { server(resolve("SELECT c.study_server_id FROM course_channels ch JOIN courses c ON c.id=ch.course_id WHERE ch.id=?",id)); }
    public void serverChannel(UUID id) { server(resolve("SELECT study_server_id FROM study_server_channels WHERE id=?",id)); }
    private UUID resolve(String sql,UUID id) {
        return jdbc.query(sql,(rs,row)->rs.getObject(1,UUID.class),id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Community scope is unavailable"));
    }
}
