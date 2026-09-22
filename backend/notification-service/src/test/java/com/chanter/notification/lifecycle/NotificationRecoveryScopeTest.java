package com.chanter.notification.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.lifecycle.*;
import com.chanter.notification.domain.*;
import com.chanter.notification.infra.JdbcNotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class NotificationRecoveryScopeTest {
    @Test void restoredHistoricalPreviewRequiresSeparateVerifiedUnionAndCannotReappearAfterAtomicReconciliation() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var jdbc=new JdbcTemplate(ds); var manager=new DataSourceTransactionManager(ds); var tx=new TransactionTemplate(manager);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var snapshots=new ExportSnapshotStore(jdbc,tx,mapper,java.time.Clock.systemUTC(),"notification");
        var factory=new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        var config=new NotificationTerminalConfiguration();
        var terminal=config.notificationTerminalStore(jdbc,manager,snapshots,true,factory.getBeanProvider(RecoveryScopeStore.class));
        var current=config.notificationDeletedScopeStore(jdbc,manager,terminal);
        UUID restore=UUID.randomUUID(),operation=UUID.randomUUID();
        var historical=config.notificationRecoveryScopeStore(jdbc,manager,current,terminal,true,restore.toString());
        factory.registerSingleton("historical",historical);
        var repository=new JdbcNotificationRepository(jdbc,terminal);
        UUID server=UUID.randomUUID(),course=UUID.randomUUID(),oldCourse=UUID.randomUUID();
        var own=notification(course); var older=notification(oldCourse);
        tx.executeWithoutResult(status -> { repository.upsert(own); repository.upsert(older); });
        UUID event=UUID.randomUUID(); var now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var entry=new TerminalJournal.Entry(1,event,"STUDY_SERVER",server,"DELETE",now,TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,
                TerminalJournal.digest(1,event,"STUDY_SERVER",server,now,TerminalJournal.GENESIS));
        tx.executeWithoutResult(status -> terminal.applyTerminal(entry));
        current.accept(new DeletedScope.Import(entry,page(entry,"COURSE",List.of(course),entry.digest())));
        current.accept(new DeletedScope.Import(entry,page(entry,"CHANNEL",List.of(),entry.digest())));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Integer.class)).isEqualTo(2);
        var cleanup=tx.execute(status -> terminal.cleanup(entry)); assertThat(cleanup).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
        var union=java.util.stream.Stream.of(course,oldCourse).sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        historical.accept(new RecoveryScope.Import(restore,operation,current.scopeDigest(entry,"COURSE"),entry,
                page(entry,"COURSE",union,historical.basis(entry,"COURSE"))));
        var last=new RecoveryScope.Import(restore,operation,current.scopeDigest(entry,"CHANNEL"),entry,
                page(entry,"CHANNEL",List.of(),historical.basis(entry,"CHANNEL")));
        tx.executeWithoutResult(status -> { historical.accept(last); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Integer.class)).isZero(); status.setRollbackOnly(); });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Integer.class)).isEqualTo(2);
        String token="fixture-notification-internal-token-at-least-32";
        var http=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new SourceDeletedScopeController(current,historical,mapper,token)).build();
        String route="/api/v1/internal/lifecycle/deleted-study-servers/"+server+"/scope/recovery/import";
        byte[] body=mapper.writeValueAsBytes(last);
        http.perform(post(route).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        var wrong=new RecoveryScope.Import(UUID.randomUUID(),operation,last.originalScopeDigest(),entry,last.page());
        http.perform(post(route).header(AuthHeaders.INTERNAL_SERVICE_TOKEN,token).contentType("application/json").content(mapper.writeValueAsBytes(wrong)))
                .andExpect(status().isBadRequest());
        for(int retry=0;retry<2;retry++) http.perform(post(route).header(AuthHeaders.INTERNAL_SERVICE_TOKEN,token).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scope.ready").value(true));
        tx.executeWithoutResult(status -> { repository.upsert(own); repository.upsert(older); });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Integer.class)).isZero();
        cleanup=tx.execute(status -> terminal.cleanup(entry)); assertThat(cleanup).isEqualTo(TerminalReapplyStore.Cleanup.COMPLETE);
    }
    private static Notification notification(UUID course) {
        return new Notification(UUID.randomUUID(),UUID.randomUUID(),NotificationKind.SUPPORT_QUESTION_ANSWERED,NotificationFilterBucket.OTHER,
                "private title","private preview",null,"/app/inbox","SUPPORT_QUESTION",UUID.randomUUID(),null,course,null,null,Instant.now(),null,null);
    }
    private static DeletedScope.Page page(TerminalJournal.Entry entry,String kind,List<UUID> ids,String basis) {
        String digest=DeletedScope.startDigest(basis,kind,ids.size());
        for(UUID id:ids) digest=DeletedScope.nextDigest(digest,id);
        return new DeletedScope.Page(1,entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),kind,DeletedScope.START,ids.size(),digest,ids,null);
    }
}
