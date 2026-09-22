package com.chanter.auth.lifecycle;

import static org.assertj.core.api.Assertions.*;
import com.chanter.common.events.*;
import com.chanter.common.lifecycle.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:source-deletion;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "chanter.events.dispatch-enabled=false"})
@ActiveProfiles("test")
class SourceDeletionJobsTest {
    @Autowired SourceDeletionJobs jobs;
    @Autowired TerminalJournalStore journal;
    @Autowired AccountDeletionProtocol protocol;
    @Autowired JdbcTemplate jdbc;
    @Test void sourceAuthorityDispatchAndCursorAreAtomicAndReplayKeepsTheOriginalEntry() {
        var request=new AccountDeletionProtocol.SourceRequest(UUID.randomUUID(),"RESOURCE",UUID.randomUUID());
        var event=event("media",AccountDeletionProtocol.SOURCE_REQUEST,request,1);
        jdbc.execute("ALTER TABLE durable_outbox ADD CONSTRAINT fixture_terminal_dispatch_failure CHECK(NOT(destination='lifecycle-message' AND kind='LIFECYCLE_TERMINAL_DELETE'))");
        try { assertThatThrownBy(() -> jobs.request(event)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
        finally { jdbc.execute("ALTER TABLE durable_outbox DROP CONSTRAINT fixture_terminal_dispatch_failure"); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_terminal_journal WHERE target_id=?",Integer.class,request.targetId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_event_cursor WHERE aggregate_key=?",Integer.class,event.aggregateKey())).isZero();
        jobs.request(event); var first=jobs.progress(request.jobId()); jobs.request(event);
        assertThat(jobs.progress(request.jobId())).isEqualTo(first);
        assertThat(first.parts()).hasSize(7); assertThat(first.state()).isEqualTo("ERASING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind=? AND aggregate_key=?",Integer.class,AccountDeletionProtocol.TERMINAL,event.aggregateKey())).isEqualTo(6);
        for(String source:AccountExportProtocol.SOURCES) if(!source.equals("auth")) {
            var receipt=new AccountDeletionProtocol.Receipt(request.jobId(),source,request.targetKind(),request.targetId(),"COMPLETE",first.terminalRevision(),first.terminalDigest());
            var delivered=new DurableEvent(UUID.randomUUID(),1,source,2,AccountDeletionProtocol.RECEIPT,event.aggregateKey(),protocol.encode(receipt));
            jobs.accept(delivered); jobs.accept(delivered);
        }
        assertThat(jobs.progress(request.jobId()).state()).isEqualTo("WAITING_FOR_REPLICA");
        journal.acknowledge(new TerminalJournal.Checkpoint(first.terminalRevision(),first.terminalDigest(),UUID.randomUUID()));
        assertThat(jobs.progress(request.jobId()).state()).isEqualTo("COMPLETE");
        var changed=new AccountDeletionProtocol.SourceRequest(UUID.randomUUID(),"RESOURCE",request.targetId());
        assertThatThrownBy(() -> jobs.request(event("media",AccountDeletionProtocol.SOURCE_REQUEST,changed,3))).hasMessageContaining("Invalid source deletion authority");
    }
    @Test void fixedSourceKindJobAndReceiptAuthorityRejectSpoofing() {
        var request=new AccountDeletionProtocol.SourceRequest(UUID.randomUUID(),"STUDY_SERVER",UUID.randomUUID());
        assertThatThrownBy(() -> jobs.request(event("media",AccountDeletionProtocol.SOURCE_REQUEST,request,1))).isInstanceOf(IllegalArgumentException.class);
        jobs.request(event("community",AccountDeletionProtocol.SOURCE_REQUEST,request,1));
        var progress=jobs.progress(request.jobId());
        var bad=new AccountDeletionProtocol.Receipt(request.jobId(),"search",request.targetKind(),request.targetId(),"COMPLETE",progress.terminalRevision(),"f".repeat(64));
        assertThatThrownBy(() -> jobs.accept(new DurableEvent(UUID.randomUUID(),1,"search",1,AccountDeletionProtocol.RECEIPT,
                AccountDeletionProtocol.key(request.targetKind(),request.targetId()),protocol.encode(bad)))).isInstanceOf(IllegalArgumentException.class);
        assertThat(jobs.progress(request.jobId()).parts().stream().filter(part -> part.source().equals("search")).findFirst().orElseThrow().state()).isEqualTo("PENDING");
        assertThatThrownBy(() -> jobs.request(event("community",AccountDeletionProtocol.SOURCE_REQUEST,
                new AccountDeletionProtocol.SourceRequest(UUID.randomUUID(),"ACCOUNT",UUID.randomUUID()),1))).isInstanceOf(IllegalArgumentException.class);
    }
    private DurableEvent event(String source,String kind,AccountDeletionProtocol.SourceRequest request,long revision) {
        return new DurableEvent(UUID.randomUUID(),1,source,revision,kind,AccountDeletionProtocol.key(request.targetKind(),request.targetId()),protocol.encode(request));
    }
}
