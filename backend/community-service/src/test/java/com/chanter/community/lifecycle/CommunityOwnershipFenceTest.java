package com.chanter.community.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.chanter.community.domain.*;
import com.chanter.community.infra.JdbcStudyServerRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class CommunityOwnershipFenceTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    CommunityOwnershipFence fence;
    JdbcStudyServerRepository servers;
    @BeforeEach void setup() {
        var data=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(data).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(data); tx=new TransactionTemplate(new DataSourceTransactionManager(data));
        fence=new CommunityOwnershipFence(jdbc);
        var terminal=new com.chanter.common.lifecycle.TerminalReapplyStore(jdbc,tx,"community",
                entry -> com.chanter.common.lifecycle.TerminalReapplyStore.Cleanup.PENDING);
        servers=new JdbcStudyServerRepository(JdbcClient.create(jdbc),data,fence,terminal);
    }
    @Test void actualServerCreationPreventsPreparationAndUnresolvedOwnershipNeverFreezesTheOwner() {
        UUID owner=UUID.randomUUID(),job=UUID.randomUUID();
        tx.executeWithoutResult(status -> servers.save(server(owner)));
        assertThat(tx.<CommunityOwnershipFence.Preparation>execute(status -> fence.prepare(owner,job))).isEqualTo(CommunityOwnershipFence.Preparation.BLOCKED_OWNERSHIP);
        tx.executeWithoutResult(status -> servers.save(server(owner)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_servers WHERE owner_user_id=?",Integer.class,owner)).isEqualTo(2);
    }
    @Test void rollbackReleasesPreparationAndOnlyMatchingCancellationCanReopenAnUnfinishedAccount() {
        UUID owner=UUID.randomUUID(),job=UUID.randomUUID(),later=UUID.randomUUID();
        tx.executeWithoutResult(status -> { fence.prepare(owner,job); status.setRollbackOnly(); });
        assertThat(tx.<CommunityOwnershipFence.Preparation>execute(status -> fence.prepare(owner,later))).isEqualTo(CommunityOwnershipFence.Preparation.PREPARED);
        assertThat(tx.<CommunityOwnershipFence.Preparation>execute(status -> fence.prepare(owner,later))).isEqualTo(CommunityOwnershipFence.Preparation.PREPARED);
        assertThat(tx.<Boolean>execute(status -> fence.release(owner,job))).isFalse();
        assertThatThrownBy(() -> tx.execute(status -> servers.save(server(owner)))).isInstanceOf(ResponseStatusException.class);
        assertThat(tx.<Boolean>execute(status -> fence.release(owner,later))).isTrue();
        tx.executeWithoutResult(status -> servers.save(server(owner)));
        tx.executeWithoutResult(status -> fence.close(owner));
        assertThat(tx.<Boolean>execute(status -> fence.release(owner,later))).isFalse();
        assertThatThrownBy(() -> tx.execute(status -> fence.prepare(owner,UUID.randomUUID()))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> tx.execute(status -> servers.save(server(owner)))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void preparationAndCreationSerializeInBothCommitOrdersWithoutAnOwnershipGap() throws Exception {
        for(boolean creationFirst:List.of(true,false)) {
            UUID owner=UUID.randomUUID(),job=UUID.randomUUID();
            var held=new CountDownLatch(1); var release=new CountDownLatch(1); var started=new CountDownLatch(1);
            try(var workers=Executors.newFixedThreadPool(2)) {
                var first=workers.submit(() -> tx.executeWithoutResult(status -> {
                    if(creationFirst) servers.save(server(owner)); else fence.prepare(owner,job);
                    held.countDown();
                    try { if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout"); }
                    catch(InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
                }));
                assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();
                var second=workers.submit(() -> {
                    started.countDown();
                    if(creationFirst) return tx.execute(status -> fence.prepare(owner,job)).name();
                    try { tx.execute(status -> servers.save(server(owner))); return "CREATED"; }
                    catch(ResponseStatusException denied) { return denied.getReason(); }
                });
                assertThat(started.await(5,TimeUnit.SECONDS)).isTrue(); release.countDown();
                first.get(5,TimeUnit.SECONDS);
                assertThat(second.get(5,TimeUnit.SECONDS)).isEqualTo(creationFirst?"BLOCKED_OWNERSHIP":"ACCOUNT_DELETION_PREPARED");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_servers WHERE owner_user_id=?",Integer.class,owner)).isEqualTo(creationFirst?1:0);
            } finally { release.countDown(); }
        }
    }
    private static StudyServer server(UUID owner) {
        return new StudyServer(UUID.randomUUID(),"Owned fixture",null,StudyServerType.PERSONAL,
                new OwnerRole(owner,StudyServerRole.STUDY_SERVER_OWNER),SaasPlanTier.STARTER,List.of(),Instant.now());
    }
}
