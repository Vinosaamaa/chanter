package com.chanter.media.lifecycle;

import com.chanter.common.lifecycle.*;
import com.chanter.common.events.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Worker fixtures supply explicit synthetic canonical authority; cross-service fixtures exercise real auth allocation separately. */
public final class TerminalDeletionTestSupport {
    private TerminalDeletionTestSupport() { }
    public static void deliverResource(ApplicationContext context,UUID target) {
        var jdbc=context.getBean(JdbcTemplate.class);
        var protocol=context.getBean(AccountDeletionProtocol.class);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_terminal_targets WHERE target_kind='RESOURCE' AND target_id=?",Integer.class,target)>0) return;
        UUID job=jdbc.queryForObject("SELECT job_id FROM lifecycle_source_requests WHERE target_id=?",UUID.class,target);
        long revision=jdbc.queryForObject("SELECT COALESCE(MAX(revision),0)+1 FROM lifecycle_terminal_targets",Long.class);
        UUID event=UUID.randomUUID(); Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var entry=new TerminalJournal.Entry(revision,event,"RESOURCE",target,"DELETE",now,TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,
                TerminalJournal.digest(revision,event,"RESOURCE",target,now,TerminalJournal.GENESIS));
        context.getBean(AccountDeletionParticipant.class).accept(new DurableEvent(UUID.randomUUID(),1,"auth",revision,AccountDeletionProtocol.TERMINAL,
                AccountDeletionProtocol.key("RESOURCE",target),protocol.encode(new AccountDeletionProtocol.Terminal(job,entry))));
    }
}
