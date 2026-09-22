package com.chanter.common.lifecycle;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class RecoveryScopeStoreTest {
    static final UUID RESTORE=UUID.fromString("33333333-3333-4333-8333-333333333333");
    JdbcTemplate jdbc; TransactionTemplate tx; TerminalJournal.Entry entry; DeletedScopeStore current; RecoveryScopeStore recovery;
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute(TerminalReapplyStore.SCHEMA); jdbc.execute(DeletedScopeStore.SCHEMA); jdbc.execute(DeletedScopeStore.RECOVERY_SCHEMA);
        var event=UUID.fromString("11111111-1111-4111-8111-111111111111"); var target=UUID.fromString("22222222-2222-4222-8222-222222222222");
        var now=Instant.parse("2026-09-19T06:00:00.120Z");
        entry=new TerminalJournal.Entry(1,event,"STUDY_SERVER",target,"DELETE",now,TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,
                TerminalJournal.digest(1,event,"STUDY_SERVER",target,now,TerminalJournal.GENESIS));
        var terminal=new TerminalReapplyStore(jdbc,tx,"notification",value -> TerminalReapplyStore.Cleanup.PENDING);
        tx.executeWithoutResult(status -> terminal.applyTerminal(entry));
        current=new DeletedScopeStore(jdbc,tx,value -> terminal.cleanup(value),value -> {});
        recovery=new RecoveryScopeStore(jdbc,tx,current,RESTORE,value -> terminal.cleanup(value),value -> {});
    }
    @Test void historicalUnionRetainsOriginalArchiveAndReusesItsImmutableRestoreBindingAcrossLaterPrefixOperations() {
        seedCurrent();
        var ids=new java.util.ArrayList<>(DeletedScopeStoreTest.IDS);
        ids.add(UUID.fromString("00000000-0000-0000-0000-000000000002")); ids.sort(java.util.Comparator.comparing(UUID::toString));
        var request=request(UUID.randomUUID(),"COURSE",ids);
        var first=recovery.accept(request);
        assertThat(first.scope().ready()).isTrue();
        UUID later=UUID.randomUUID();
        var repeated=recovery.accept(new RecoveryScope.Import(RESTORE,later,request.originalScopeDigest(),entry,request.page()));
        assertThat(repeated.scope()).isEqualTo(first.scope()); assertThat(repeated.recoveryId()).isEqualTo(later);
        assertThat(current.page(entry.targetId(),1,entry.eventId(),entry.digest(),"COURSE",DeletedScope.START,256).ids()).containsExactlyElementsOf(DeletedScopeStoreTest.IDS);
        assertThat(recovery.page(later,entry,"COURSE",DeletedScope.START,256).page().ids()).containsExactlyElementsOf(ids);
        assertThat(recovery.basis(entry,"COURSE")).isEqualTo("03327b158e2536a1325cfdef9f19a0feaf58f49e41d8fa549c266db60cbbbc49");
        assertThat(first.scope().scopeDigest()).isEqualTo("a3dfbf38520153bfb607a2b163b9de71f4ea5287364b035ade2b8079bd1e9c03");
    }
    @Test void ordinaryRuntimeMissingOriginalKindWrongRestoreOrChangedArchiveCannotAuthorizeDerivedScope() {
        assertThatThrownBy(() -> recovery.basis(entry,"COURSE")).isInstanceOf(IllegalArgumentException.class);
        current.accept(new DeletedScope.Import(entry,page("COURSE",DeletedScopeStoreTest.IDS,entry.digest())));
        assertThatThrownBy(() -> recovery.basis(entry,"COURSE")).isInstanceOf(IllegalArgumentException.class);
        current.accept(new DeletedScope.Import(entry,page("CHANNEL",List.of(),entry.digest())));
        var request=request(UUID.randomUUID(),"COURSE",DeletedScopeStoreTest.IDS);
        assertThatThrownBy(() -> recovery.accept(new RecoveryScope.Import(UUID.randomUUID(),request.recoveryId(),request.originalScopeDigest(),entry,request.page())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recovery.accept(new RecoveryScope.Import(RESTORE,request.recoveryId(),"f".repeat(64),entry,request.page())))
                .isInstanceOf(IllegalArgumentException.class);
        var ordinary=new RecoveryScopeStore(jdbc,tx,current,null,value -> {},value -> {});
        assertThatThrownBy(() -> ordinary.accept(request)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_recovery_scope_ids",Integer.class)).isZero();
    }
    @Test void aDigestConsistentDerivedPageCannotDropCurrentArchivedIds() {
        seedCurrent();
        assertThatThrownBy(() -> recovery.accept(request(UUID.randomUUID(),"COURSE",DeletedScopeStoreTest.IDS.subList(0,3))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("omits current archived scope");
        assertThat(recovery.ready(entry,"COURSE")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_recovery_scope_ids",Integer.class)).isZero();
    }
    private void seedCurrent() {
        current.accept(new DeletedScope.Import(entry,page("COURSE",DeletedScopeStoreTest.IDS,entry.digest())));
        current.accept(new DeletedScope.Import(entry,page("CHANNEL",List.of(),entry.digest())));
    }
    private RecoveryScope.Import request(UUID operation,String kind,List<UUID> ids) {
        return new RecoveryScope.Import(RESTORE,operation,current.scopeDigest(entry,kind),entry,page(kind,ids,recovery.basis(entry,kind)));
    }
    private DeletedScope.Page page(String kind,List<UUID> ids,String basis) {
        String digest=DeletedScope.startDigest(basis,kind,ids.size());
        for(UUID id:ids) digest=DeletedScope.nextDigest(digest,id);
        return new DeletedScope.Page(1,entry.targetId(),1,entry.eventId(),entry.digest(),kind,DeletedScope.START,ids.size(),digest,ids,null);
    }
}
