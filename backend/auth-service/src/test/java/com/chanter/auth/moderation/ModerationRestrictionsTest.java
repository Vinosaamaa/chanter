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
    @Autowired ModerationRestrictions restrictions;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AuthSessionService sessions;
    @Autowired AuthUserRepository users;

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

    private void seedReport(UUID report, UUID user) {
        jdbc.update("""
                INSERT INTO moderation_reports(id,reporter_id,target_type,target_id,reason,evidence,status,created_at,updated_at)
                VALUES(?,?,'USER',?,'Abuse report','{}','NEW',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, report, UUID.randomUUID(), user);
    }
}
