package com.chanter.common.lifecycle;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class DeletedScopeStoreTest {
    static final List<UUID> IDS=List.of(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff"),UUID.fromString("80000000-0000-0000-0000-000000000000"),
            UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    JdbcTemplate jdbc; TransactionTemplate tx; TerminalReapplyStore terminal; DeletedScopeStore scopes;
    TerminalJournal.Entry entry;
    AtomicBoolean fail=new AtomicBoolean();
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute(TerminalReapplyStore.SCHEMA); jdbc.execute(DeletedScopeStore.SCHEMA);
        terminal=new TerminalReapplyStore(jdbc,tx,"message",value -> TerminalReapplyStore.Cleanup.PENDING);
        var event=UUID.fromString("11111111-1111-4111-8111-111111111111");
        var target=UUID.fromString("22222222-2222-4222-8222-222222222222");
        var now=Instant.parse("2026-09-19T06:00:00.120Z");
        entry=new TerminalJournal.Entry(1,event,"STUDY_SERVER",target,"DELETE",now,TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,
                TerminalJournal.digest(1,event,"STUDY_SERVER",target,now,TerminalJournal.GENESIS));
        scopes=new DeletedScopeStore(jdbc,tx,value -> terminal.cleanup(value),value -> {
            if(fail.get()) throw new IllegalStateException("Owning reconciliation failed");
            terminal.recordCleanup(value,TerminalReapplyStore.Cleanup.PENDING,TerminalReapplyStore.Cleanup.COMPLETE);
        });
    }
    @Test void originalAuthorityRequiredAndFinalImportCommitsWithReconciliationWhileExactReplayIsIdempotent() {
        var first=page(DeletedScope.START,IDS.subList(0,2),IDS.get(1));
        assertThatThrownBy(() -> scopes.accept(new DeletedScope.Import(entry,first))).isInstanceOf(IllegalArgumentException.class);
        tx.executeWithoutResult(status -> terminal.applyTerminal(entry));
        assertThat(scopes.accept(new DeletedScope.Import(entry,first)).ready()).isFalse();
        assertThat(scopes.ready(entry,"COURSE")).isFalse();
        var last=page(IDS.get(1),IDS.subList(2,4),null);
        fail.set(true);
        assertThatThrownBy(() -> scopes.accept(new DeletedScope.Import(entry,last))).hasMessageContaining("Owning reconciliation failed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_scope_import_ids",Integer.class)).isEqualTo(2);
        var cleanup=tx.execute(status -> terminal.cleanup(entry));
        assertThat(cleanup).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
        fail.set(false);
        var receipt=scopes.accept(new DeletedScope.Import(entry,last));
        assertThat(receipt.ready()).isTrue(); assertThat(receipt.receivedCount()).isEqualTo(4);
        assertThat(scopes.accept(new DeletedScope.Import(entry,last))).isEqualTo(receipt);
        assertThat(scopes.accept(new DeletedScope.Import(entry,first))).isEqualTo(receipt);
        assertThat(scopes.page(entry.targetId(),1,entry.eventId(),entry.digest(),"COURSE",DeletedScope.START,256).ids()).containsExactlyElementsOf(IDS);
    }
    @Test void repeatedIdsReorderedUnsignedBoundaryWrongCursorOrChangedCountCannotFinishEvenWithTheRightNumberOfIds() {
        tx.executeWithoutResult(status -> terminal.applyTerminal(entry));
        var first=page(DeletedScope.START,IDS.subList(0,2),IDS.get(1));
        scopes.accept(new DeletedScope.Import(entry,first));
        for(var invalid:List.of(page(IDS.get(1),List.of(IDS.get(1),IDS.get(3)),null),
                page(IDS.get(1),List.of(IDS.get(3),IDS.get(2)),null),page(IDS.get(0),IDS.subList(2,4),null),
                page(IDS.get(1),List.of(IDS.get(2)),null),page(DeletedScope.START,IDS.subList(0,1),IDS.get(0)))) {
            assertThatThrownBy(() -> scopes.accept(new DeletedScope.Import(entry,invalid))).isInstanceOf(IllegalArgumentException.class);
        }
        var valid=page(IDS.get(1),IDS.subList(2,4),null);
        var changed=new DeletedScope.Page(1,entry.targetId(),1,entry.eventId(),entry.digest(),"COURSE",valid.after(),4,"f".repeat(64),valid.ids(),null);
        assertThatThrownBy(() -> scopes.accept(new DeletedScope.Import(entry,changed))).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_scope_import_ids",Integer.class)).isEqualTo(2);
        assertThat(scopes.ready(entry,"COURSE")).isFalse();
    }
    @Test void zeroCountRequiresAnExplicitValidFinalPageAndIsNotMissingScope() {
        tx.executeWithoutResult(status -> terminal.applyTerminal(entry));
        assertThat(scopes.ready(entry,"CHANNEL")).isFalse();
        var empty=new DeletedScope.Page(1,entry.targetId(),1,entry.eventId(),entry.digest(),"CHANNEL",DeletedScope.START,0,
                DeletedScope.startDigest(entry.digest(),"CHANNEL",0),List.of(),null);
        assertThat(scopes.accept(new DeletedScope.Import(entry,empty)).ready()).isTrue();
        assertThat(scopes.ready(entry,"CHANNEL")).isTrue();
    }
    @Test void fixtureUsesCanonicalTextOrderingAcrossTheJavaSignedUuidBoundary() {
        assertThat(IDS.get(1).compareTo(IDS.get(2))).isPositive();
        assertThat(IDS.get(1).toString().compareTo(IDS.get(2).toString())).isNegative();
        assertThat(entry.digest()).isEqualTo("faabc05c784b94a62e6a28b0260b5531ac3eda3520a019d5af3dc9e3ccbdaaac");
        assertThat(digest()).isEqualTo("97231f4c00a42affd8a4517b3b0eeb7a4dce2db8c322eda8a29c9529add08c73");
    }
    private String digest() {
        String result=DeletedScope.startDigest(entry.digest(),"COURSE",IDS.size());
        for(UUID id:IDS) result=DeletedScope.nextDigest(result,id);
        return result;
    }
    private DeletedScope.Page page(UUID after,List<UUID> ids,UUID next) {
        return new DeletedScope.Page(1,entry.targetId(),1,entry.eventId(),entry.digest(),"COURSE",after,4,digest(),ids,next);
    }
}
