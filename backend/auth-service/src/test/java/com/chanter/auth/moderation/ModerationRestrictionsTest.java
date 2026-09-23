package com.chanter.auth.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.chanter.auth.application.AuthSessionService;
import com.chanter.auth.application.AuthUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class ModerationRestrictionsTest {
    @Autowired JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ModerationRestrictions restrictions;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AuthSessionService sessions;
    @Autowired AuthUserRepository users;
    @Autowired com.chanter.auth.lifecycle.TerminalJournalStore journal;

    @Test void terminalAuthorityOutlivesRestrictionReversalAndBlocksEveryAccountAndSourceEntryPoint() {
        UUID user=UUID.randomUUID(),report=UUID.randomUUID(),operation=UUID.randomUUID();
        jdbc.update("INSERT INTO auth_users(id,email,password_hash,display_name,email_verified,created_at) VALUES(?,?,'unusable-test-password','Member',TRUE,CURRENT_TIMESTAMP)",
                user,user+"@terminal.test");
        var account=users.findById(user).orElseThrow();
        var session=sessions.issueSessionForUser(account);
        seedReport(report,user);
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> restrictions.add(operation,report,"USER",user,UUID.randomUUID(),
                "Confirmed abuse",Instant.now().plusSeconds(3600),UUID.randomUUID()));
        UUID resource=UUID.randomUUID(),server=UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            journal.append("ACCOUNT",user); journal.append("RESOURCE",resource); journal.append("STUDY_SERVER",server);
            restrictions.revoke(report,operation,UUID.randomUUID(),"Temporary restriction reversed",UUID.randomUUID());
        });
        var accountTarget=new com.chanter.common.auth.ModerationAccess.Target("USER",user);
        var resourceTarget=new com.chanter.common.auth.ModerationAccess.Target("RESOURCE",resource);
        var serverTarget=new com.chanter.common.auth.ModerationAccess.Target("STUDY_SERVER",server);
        assertThat(restrictions.restrictedSources(java.util.List.of(accountTarget,resourceTarget,serverTarget,
                new com.chanter.common.auth.ModerationAccess.Target("MESSAGE",resource),
                new com.chanter.common.auth.ModerationAccess.Target("DM",user)),Instant.now().plusSeconds(7200)))
                .containsExactlyInAnyOrder(accountTarget,resourceTarget,serverTarget);
        assertThat(restrictions.isRestricted("USER",user,Instant.now().plusSeconds(7200))).isTrue();
        assertThatThrownBy(() -> restrictions.requireActiveAccount(user)).hasMessageContaining("410").hasMessageContaining("LIFECYCLE_ACCOUNT_DELETED");
        assertThatThrownBy(() -> sessions.requireUserIdFromAccessToken("Bearer "+session.accessToken()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> sessions.issueSessionForUser(account)).hasMessageContaining("410");
        assertThatThrownBy(() -> sessions.refresh(session.refreshToken())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_reports WHERE id=?",Integer.class,report)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_audit WHERE target=?",Integer.class,"USER:"+user)).isEqualTo(2);
    }

    @Test void suspensionBlocksExistingAccessRefreshAndNewProviderSession() {
        UUID user = UUID.randomUUID();
        jdbc.update("INSERT INTO auth_users(id,email,password_hash,display_name,email_verified,created_at) VALUES(?,?,?,'Member',TRUE,CURRENT_TIMESTAMP)",
                user, user+"@moderation.test", "unusable-test-password");
        var account = users.findById(user).orElseThrow();
        var session = sessions.issueSessionForUser(account);
        UUID report = UUID.randomUUID();
        seedReport(report, user);
        jdbc.update("""
                INSERT INTO moderation_restrictions(id,report_id,target_type,target_id,actor_id,reason,starts_at,expires_at)
                VALUES(?,?,'USER',?,?,'Account abuse',?,?)
                """, UUID.randomUUID(), report, user, UUID.randomUUID(), OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now().plusHours(1));
        assertThatThrownBy(() -> sessions.requireUserIdFromAccessToken("Bearer "+session.accessToken()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> sessions.refresh(session.refreshToken()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> sessions.issueSessionForUser(account))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void restrictionEndsAtExpiryAndRevocationDoesNotEraseItsEvidence() {
        UUID user = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        seedReport(report, user);
        jdbc.update("""
                INSERT INTO moderation_restrictions(id,report_id,target_type,target_id,actor_id,reason,starts_at,expires_at)
                VALUES(?,?,'USER',?,?,'Account abuse',?,?)
                """, id, report, user, UUID.randomUUID(), OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now().plusHours(1));
        assertThat(restrictions.isRestricted("USER", user, Instant.now())).isTrue();
        var exact=new com.chanter.common.auth.ModerationAccess.Target("USER",user);
        var otherType=new com.chanter.common.auth.ModerationAccess.Target("MESSAGE",user);
        var batch=java.util.List.of(exact,otherType,new com.chanter.common.auth.ModerationAccess.Target("USER",UUID.randomUUID()));
        assertThat(restrictions.restrictedSources(batch,Instant.now())).containsExactly(exact);
        assertThat(restrictions.restrictedSources(batch,Instant.now().plusSeconds(7200))).isEmpty();
        assertThat(restrictions.isRestricted("USER", user, Instant.now().plusSeconds(7200))).isFalse();
        jdbc.update("UPDATE moderation_restrictions SET revoked_at=CURRENT_TIMESTAMP WHERE id=?", id);
        assertThat(restrictions.restrictedSources(batch,Instant.now())).isEmpty();
        assertThat(restrictions.isRestricted("USER", user, Instant.now())).isFalse();
        assertThat(jdbc.queryForObject("SELECT reason FROM moderation_restrictions WHERE id=?", String.class, id)).isEqualTo("Account abuse");
    }

    @Test void transactionFailureRollsBackRestrictionAndAuditTogether() {
        UUID user = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        seedReport(report, user);
        var tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            restrictions.add(UUID.randomUUID(), report, "USER", user, UUID.randomUUID(), "Confirmed abuse",
                    Instant.now().plusSeconds(3600), UUID.randomUUID());
            throw new IllegalStateException("Simulate failure before transaction commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(restrictions.isRestricted("USER", user, Instant.now())).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_audit WHERE target=?", Integer.class, "USER:"+user)).isZero();
    }

    @Test void aRestrictionShorterThanOneMinuteIsRejected() {
        UUID report=UUID.randomUUID(),user=UUID.randomUUID(); seedReport(report,user);
        var tx=new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> restrictions.add(UUID.randomUUID(),report,"USER",user,
                UUID.randomUUID(),"Review this account",Instant.now().plusMillis(59500),UUID.randomUUID())))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void operationInsertedAfterThePrecheckReturnsConflictWithoutAppendingAudit() {
        var racingJdbc=org.mockito.Mockito.mock(JdbcTemplate.class);
        var untouchedAudit=org.mockito.Mockito.mock(ModerationAudit.class);
        org.mockito.Mockito.when(racingJdbc.update(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object[].class))).thenThrow(new org.springframework.dao.DuplicateKeyException("Concurrent operation"));
        var service=new ModerationRestrictions(racingJdbc,untouchedAudit);
        assertThatThrownBy(() -> service.add(UUID.randomUUID(),UUID.randomUUID(),"RESOURCE",UUID.randomUUID(),
                UUID.randomUUID(),"Confirmed abuse",Instant.now().plusSeconds(3600),UUID.randomUUID()))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(409));
        org.mockito.Mockito.verifyNoInteractions(untouchedAudit);
    }

    @Test void concurrentProviderIssuanceCannotSurviveSuspensionAndReinstatement() throws Exception {
        UUID user=UUID.randomUUID(),report=UUID.randomUUID(),operation=UUID.randomUUID();
        jdbc.update("INSERT INTO auth_users(id,email,password_hash,display_name,email_verified,created_at) VALUES(?,?,'unusable-provider-password','Member',TRUE,CURRENT_TIMESTAMP)",
                user,user+"@suspension-race.test");
        seedReport(report,user);
        var account=users.findById(user).orElseThrow();
        var checked=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        var first=new java.util.concurrent.atomic.AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(call -> {
            call.callRealMethod();
            if(first.getAndSet(false)) {
                checked.countDown();
                assertThat(release.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(restrictions).requireActiveAccount(user);
        var tx=new TransactionTemplate(transactions);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var issuance=workers.submit(() -> sessions.issueSessionForUser(account));
            assertThat(checked.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var suspension=workers.submit(() -> tx.executeWithoutResult(status -> restrictions.add(operation,report,"USER",user,
                    UUID.randomUUID(),"Confirmed account abuse",Instant.now().plusSeconds(3600),UUID.randomUUID())));
            try {
                assertThatThrownBy(() -> suspension.get(200,java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally { release.countDown(); }
            var issued=issuance.get(5,java.util.concurrent.TimeUnit.SECONDS);
            suspension.get(5,java.util.concurrent.TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> restrictions.revoke(report,operation,UUID.randomUUID(),"Review reversed",UUID.randomUUID()));
            assertThatThrownBy(() -> sessions.requireActiveAccessSession("Bearer "+issued.accessToken()))
                    .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                            failure -> assertThat(failure.getStatusCode().value()).isEqualTo(401));
            assertThatThrownBy(() -> sessions.refresh(issued.refreshToken()))
                    .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                            failure -> assertThat(failure.getStatusCode().value()).isEqualTo(401));
        } finally { release.countDown(); }
    }

    private void seedReport(UUID report, UUID user) {
        jdbc.update("""
                INSERT INTO moderation_reports(id,reporter_id,target_type,target_id,reason,evidence,status,created_at,updated_at)
                VALUES(?,?,'USER',?,'Abuse report','{}','NEW',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, report, UUID.randomUUID(), user);
    }
}
