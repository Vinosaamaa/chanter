package com.chanter.agent.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.lifecycle.*;
import com.chanter.agent.domain.ResourceChunk;
import com.chanter.agent.infra.JdbcResourceChunkRepository;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class AgentRecoveryScopeTest {
    @Test void historicalCourseOnlyChunksRequireBothDerivedKindsAndTheirPermanentMarkersSurviveReplay() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").placeholders(Map.of("testVectorDatabase","true")).load().migrate();
        var jdbc=new JdbcTemplate(ds); var manager=new DataSourceTransactionManager(ds); var tx=new TransactionTemplate(manager);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var snapshots=new ExportSnapshotStore(jdbc,tx,mapper,java.time.Clock.systemUTC(),"agent");
        var factory=new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        var chunks=new JdbcResourceChunkRepository(JdbcClient.create(jdbc));
        var config=new AgentTerminalConfiguration();
        var terminal=config.agentTerminalStore(jdbc,manager,snapshots,chunks,mapper,true,factory.getBeanProvider(RecoveryScopeStore.class));
        var current=config.agentDeletedScopeStore(jdbc,manager,terminal);
        UUID restore=UUID.randomUUID(),operation=UUID.randomUUID();
        var historical=config.agentRecoveryScopeStore(jdbc,manager,current,terminal,true,restore.toString());
        factory.registerSingleton("historical",historical);
        UUID server=UUID.randomUUID(),course=UUID.randomUUID(),oldCourse=UUID.randomUUID(),resource=UUID.randomUUID();
        var chunk=new ResourceChunk(UUID.randomUUID(),resource,oldCourse,0,0,8,"retained","a".repeat(64),"old.txt",Instant.now());
        // A pre-extraction-version resource has its parent only in the chunk row.
        tx.executeWithoutResult(status -> chunks.replaceAllForResource(resource,List.of(chunk)));
        UUID event=UUID.randomUUID(); var now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var entry=new TerminalJournal.Entry(1,event,"STUDY_SERVER",server,"DELETE",now,TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,
                TerminalJournal.digest(1,event,"STUDY_SERVER",server,now,TerminalJournal.GENESIS));
        var head=new TerminalJournal.Watermark(1,entry.digest());
        var page=new TerminalJournal.Page(2,new TerminalJournal.Watermark(0,TerminalJournal.GENESIS),head,List.of(entry),head);
        String token="fixture-agent-terminal-private-token";
        var http=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new SourceTerminalRecoveryController(terminal,mapper,token),
                new SourceDeletedScopeController(current,historical,mapper,token)).build();
        http.perform(post("/api/v1/internal/lifecycle/journal/reapply").contentType("application/json").content(mapper.writeValueAsBytes(page)))
                .andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/internal/lifecycle/journal/reapply").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,token)
                .contentType("application/json").content(mapper.writeValueAsBytes(page))).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("agent"));
        current.accept(new DeletedScope.Import(entry,scope(entry,"COURSE",List.of(course),entry.digest())));
        current.accept(new DeletedScope.Import(entry,scope(entry,"CHANNEL",List.of(),entry.digest())));
        assertThat(chunks.findByResourceId(resource)).hasSize(1);
        var union=java.util.stream.Stream.of(course,oldCourse).sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        historical.accept(new RecoveryScope.Import(restore,operation,current.scopeDigest(entry,"COURSE"),entry,
                scope(entry,"COURSE",union,historical.basis(entry,"COURSE"))));
        var last=new RecoveryScope.Import(restore,operation,current.scopeDigest(entry,"CHANNEL"),entry,
                scope(entry,"CHANNEL",List.of(),historical.basis(entry,"CHANNEL")));
        tx.executeWithoutResult(status -> { historical.accept(last); assertThat(chunks.findByResourceId(resource)).isEmpty(); status.setRollbackOnly(); });
        assertThat(chunks.findByResourceId(resource)).hasSize(1);
        String route="/api/v1/internal/lifecycle/deleted-study-servers/"+server+"/scope/recovery/import";
        for(int retry=0;retry<2;retry++) http.perform(post(route).header(AuthHeaders.INTERNAL_SERVICE_TOKEN,token)
                .contentType("application/json").content(mapper.writeValueAsBytes(last))).andExpect(status().isOk()).andExpect(jsonPath("$.scope.ready").value(true));
        assertThat(chunks.findByResourceId(resource)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT deleted FROM resource_index_lifecycle WHERE resource_id=?",Boolean.class,resource)).isTrue();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> chunks.replaceAllForResource(resource,List.of(chunk))))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(terminal.reapply(page).pendingTargets()).isEqualTo(1);
    }
    private static DeletedScope.Page scope(TerminalJournal.Entry entry,String kind,List<UUID> ids,String basis) {
        String digest=DeletedScope.startDigest(basis,kind,ids.size()); for(UUID id:ids) digest=DeletedScope.nextDigest(digest,id);
        return new DeletedScope.Page(1,entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),kind,DeletedScope.START,ids.size(),digest,ids,null);
    }
}
