package com.chanter.common.lifecycle;

import static org.assertj.core.api.Assertions.*;
import com.chanter.common.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

class ServerPayloadCleanupTest {
    @Test void exactRetainedIdsAndVerifiedScopeEraseClaimedAndHistoricalCopiesAcrossPagesAtomically() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute(TerminalReapplyStore.SCHEMA);jdbc.execute(DurableOutbox.SCHEMA);jdbc.execute(DeletedScopeStore.SCHEMA);
        jdbc.execute("CREATE TABLE lifecycle_erased_content(target_kind VARCHAR(20),target_id UUID,source_kind VARCHAR(32),source_id UUID)");
        var mapper=new ObjectMapper();var clock=Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"),ZoneOffset.UTC);
        var outbox=new DurableOutbox(jdbc,tx,"message",clock);var cleanup=new ServerPayloadCleanup(jdbc,mapper);
        var terminal=new TerminalReapplyStore(jdbc,tx,"message",e -> TerminalReapplyStore.Cleanup.PENDING);
        UUID server=UUID.randomUUID(),course=UUID.randomUUID(),question=UUID.randomUUID(),event=UUID.randomUUID();
        var entry=new TerminalJournal.Entry(1,event,"STUDY_SERVER",server,"DELETE",clock.instant(),TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,
                TerminalJournal.digest(1,event,"STUDY_SERVER",server,clock.instant(),TerminalJournal.GENESIS));
        tx.executeWithoutResult(s -> terminal.applyTerminal(entry));
        var scopes=new DeletedScopeStore(jdbc,tx,terminal::cleanup,terminal::reconcile);
        String digest=DeletedScope.nextDigest(DeletedScope.startDigest(entry.digest(),"COURSE",1),course);
        scopes.accept(new DeletedScope.Import(entry,new DeletedScope.Page(1,server,entry.revision(),entry.eventId(),entry.digest(),"COURSE",DeletedScope.START,1,digest,List.of(course),null)));
        jdbc.update("INSERT INTO lifecycle_erased_content VALUES ('STUDY_SERVER',?,'QUESTION',?)",server,question);
        tx.executeWithoutResult(s -> {
            for(int n=0;n<257;n++) outbox.append("search","MESSAGE","MESSAGE:"+UUID.randomUUID(),"{\"courseId\":\""+course+"\",\"body\":\"private message\"}");
            outbox.append("notification","NOTIFICATION","NOTIFICATION:"+UUID.randomUUID()+":SUPPORT_QUESTION:"+question+":SUPPORT_QUESTION_ANSWERED","{\"bodyPreview\":\"legacy private preview\"}");
            outbox.append("notification","NOTIFICATION","NOTIFICATION:retained","{\"studyServerId\":\""+UUID.randomUUID()+"\",\"bodyPreview\":\"other server\"}");
        });
        var claimed=outbox.claim().orElseThrow();
        tx.executeWithoutResult(s -> {cleanup.erase(entry,"lifecycle_scope_import_ids");s.setRollbackOnly();});
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE payload='{}'",Integer.class)).isZero();
        UUID malformed=tx.execute(s -> outbox.append("search","MESSAGE","MESSAGE:malformed","not-json"));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> cleanup.erase(entry,"lifecycle_scope_import_ids"))).hasMessage("Unverified server payload scope");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE payload='{}'",Integer.class)).isZero();
        jdbc.update("DELETE FROM durable_outbox WHERE id=?",malformed);
        tx.executeWithoutResult(s -> cleanup.erase(entry,"lifecycle_scope_import_ids"));
        tx.executeWithoutResult(s -> cleanup.erase(entry,"lifecycle_scope_import_ids"));
        outbox.failed(claimed,"LATE_FAILURE");outbox.delivered(claimed);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE payload='{}' AND status='ERASED'",Integer.class)).isEqualTo(258);
        assertThat(jdbc.queryForObject("SELECT payload FROM durable_outbox WHERE aggregate_key='NOTIFICATION:retained'",String.class)).contains("other server");
        assertThatThrownBy(() -> cleanup.erase(entry,"lifecycle_scope_import_ids")).hasMessageContaining("requires transaction");
    }
}
