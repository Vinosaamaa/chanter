package com.chanter.search.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.SearchChange;
import com.chanter.common.lifecycle.*;
import com.chanter.search.domain.SearchDocumentType;
import com.chanter.search.infra.JdbcSearchIndexRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class SearchRecoveryScopeTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void verifiedScopeRemovesLegacyRowsAndFencesDelayedWritesWithAtomicRollback(boolean recovery) throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var jdbc=new JdbcTemplate(ds); var manager=new DataSourceTransactionManager(ds); var tx=new TransactionTemplate(manager);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var snapshots=new ExportSnapshotStore(jdbc,tx,mapper,java.time.Clock.systemUTC(),"search");
        var factory=new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        var config=new SearchTerminalConfiguration();
        var content=new ErasedContentReceiver("search",jdbc,tx,new com.chanter.common.events.DurableOutbox(jdbc,tx,"search",java.time.Clock.systemUTC()),mapper,
                factory.getBeanProvider(TerminalReapplyStore.class),factory.getBeanProvider(ErasedContentReceiver.Erase.class));
        var terminal=config.searchTerminalStore(jdbc,manager,snapshots,content,recovery,factory.getBeanProvider(RecoveryScopeStore.class));
        var current=config.searchDeletedScopeStore(jdbc,manager,terminal);
        UUID restore=UUID.randomUUID(),operation=UUID.randomUUID();
        var historical=config.searchRecoveryScopeStore(jdbc,manager,current,terminal,recovery,recovery ? restore.toString() : "");
        factory.registerSingleton("historical",historical);
        var repository=new JdbcSearchIndexRepository(jdbc,terminal);
        UUID server=UUID.randomUUID(),course=UUID.randomUUID(),oldCourse=UUID.randomUUID(),channel=UUID.randomUUID();
        var own=change(course,null); var older=change(oldCourse,null); var byChannel=change(null,channel); var unrelated=change(UUID.randomUUID(),null);
        tx.executeWithoutResult(status -> { repository.apply(own); repository.apply(older); repository.apply(byChannel); repository.apply(unrelated); });
        UUID event=UUID.randomUUID(); var now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var entry=new TerminalJournal.Entry(1,event,"STUDY_SERVER",server,"DELETE",now,TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,
                TerminalJournal.digest(1,event,"STUDY_SERVER",server,now,TerminalJournal.GENESIS));
        tx.executeWithoutResult(status -> terminal.applyTerminal(entry));
        current.accept(new DeletedScope.Import(entry,page(entry,"COURSE",List.of(course),entry.digest())));
        var lastCurrent=new DeletedScope.Import(entry,page(entry,"CHANNEL",List.of(channel),entry.digest()));
        tx.executeWithoutResult(status -> { current.accept(lastCurrent); status.setRollbackOnly(); });
        assertThat(count(jdbc,own.sourceId())).isEqualTo(1);
        current.accept(lastCurrent);
        if(recovery) {
            assertThat(count(jdbc,own.sourceId())).isEqualTo(1);
            var union=java.util.stream.Stream.of(course,oldCourse).sorted(java.util.Comparator.comparing(UUID::toString)).toList();
            historical.accept(new RecoveryScope.Import(restore,operation,current.scopeDigest(entry,"COURSE"),entry,
                    page(entry,"COURSE",union,historical.basis(entry,"COURSE"))));
            var last=new RecoveryScope.Import(restore,operation,current.scopeDigest(entry,"CHANNEL"),entry,
                    page(entry,"CHANNEL",List.of(channel),historical.basis(entry,"CHANNEL")));
            tx.executeWithoutResult(status -> { historical.accept(last); assertThat(count(jdbc,older.sourceId())).isZero(); status.setRollbackOnly(); });
            assertThat(count(jdbc,older.sourceId())).isEqualTo(1);
            String token="fixture-search-internal-token-at-least-32";
            var http=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new SourceDeletedScopeController(current,historical,mapper,token)).build();
            String route="/api/v1/internal/lifecycle/deleted-study-servers/"+server+"/scope/recovery/import";
            byte[] body=mapper.writeValueAsBytes(last);
            http.perform(post(route).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
            for(int retry=0;retry<2;retry++) http.perform(post(route).header(AuthHeaders.INTERNAL_SERVICE_TOKEN,token).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.scope.ready").value(true));
        }
        tx.executeWithoutResult(status -> {
            repository.apply(own); repository.apply(byChannel); repository.apply(older);
            repository.replaceStudyServerIndex(UUID.randomUUID(),List.of(new JdbcSearchIndexRepository.IndexEntry(UUID.randomUUID(),null,
                    course,"course",SearchDocumentType.RESOURCE,own.sourceId(),"stale","private content",Instant.now())));
        });
        assertThat(count(jdbc,own.sourceId())).isZero(); assertThat(count(jdbc,byChannel.sourceId())).isZero();
        assertThat(count(jdbc,older.sourceId())).isEqualTo(recovery ? 0 : 1);
        assertThat(count(jdbc,unrelated.sourceId())).isEqualTo(1);
        var cleanup=tx.execute(status -> terminal.cleanup(entry)); assertThat(cleanup).isEqualTo(TerminalReapplyStore.Cleanup.COMPLETE);
    }
    private static int count(JdbcTemplate jdbc,UUID source) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM search_index_entries WHERE source_id=?",Integer.class,source);
    }
    private static SearchChange change(UUID course,UUID channel) {
        return new SearchChange("FAQ",UUID.randomUUID(),null,course,null,channel,null,"private title","private text","/app/courses",false);
    }
    private static DeletedScope.Page page(TerminalJournal.Entry entry,String kind,List<UUID> ids,String basis) {
        String digest=DeletedScope.startDigest(basis,kind,ids.size());
        for(UUID id:ids) digest=DeletedScope.nextDigest(digest,id);
        return new DeletedScope.Page(1,entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),kind,DeletedScope.START,ids.size(),digest,ids,null);
    }
}
