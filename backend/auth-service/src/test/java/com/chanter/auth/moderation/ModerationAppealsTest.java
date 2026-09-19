package com.chanter.auth.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:moderation-appeals;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ModerationAppealsTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ModerationAppeals appeals;
    @org.springframework.test.context.bean.override.mockito.MockitoBean OperatorAccess operators;

    @Test void concurrentValidLinksCannotCreateTwoPendingAppeals() throws Exception {
        Fixture owner=fixture(true);
        appeals.request(owner.email(),owner.restriction());
        appeals.request(owner.email(),owner.restriction());
        var tokens=jdbc.queryForList("SELECT body_text FROM auth_email_outbox WHERE recipient=?",String.class,owner.email())
                .stream().map(body -> body.split("#token=",2)[1].strip()).toList();
        assertThat(tokens).hasSize(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks=tokens.stream().map(token -> executor.submit(() -> {
                start.await();
                try { appeals.submit(token,"Review this restriction",UUID.randomUUID()); return true; }
                catch(ResponseStatusException duplicate) { return false; }
            })).toList();
            start.countDown();
            int accepted=0;
            for(var task:tasks) if(task.get(10,java.util.concurrent.TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_appeals WHERE restriction_id=? AND status='PENDING'",Integer.class,owner.restriction())).isEqualTo(1);
        appeals.request(owner.email(),owner.restriction());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_email_outbox WHERE recipient=?",Integer.class,owner.email())).isEqualTo(2);
    }

    @Test void administratorCanReverseAnAppealWithAtomicRestrictionAuditAndNotice() {
        Fixture owner=fixture(true); UUID operator=fixture(true).user();
        jdbc.update("INSERT INTO platform_operators(user_id,role,granted_at) VALUES(?,'ADMIN',CURRENT_TIMESTAMP)",operator);
        when(operators.requireStepUp("operator","verified")).thenReturn(new OperatorAccess.Operator(operator,OperatorAccess.Role.ADMIN));
        appeals.request(owner.email(),owner.restriction());
        appeals.submit(sentToken(owner.email()),"Please reconsider",UUID.randomUUID());
        UUID appeal=jdbc.queryForObject("SELECT id FROM moderation_appeals WHERE restriction_id=?",UUID.class,owner.restriction());
        assertThatThrownBy(() -> appeals.resolve("operator","verified",appeal,"REVERSED","Action corrected",UUID.randomUUID().toString(),UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
        appeals.resolve("operator","verified",appeal,"REVERSED","Action corrected",owner.restriction().toString(),UUID.randomUUID());
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM moderation_restrictions WHERE id=?",Boolean.class,owner.restriction())).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM moderation_appeals WHERE id=?",String.class,appeal)).isEqualTo("REVERSED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_audit WHERE actor_id=?",Integer.class,operator)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_email_outbox WHERE recipient=?",Integer.class,owner.email())).isEqualTo(2);
        when(operators.requireStepUp("reviewer","verified")).thenReturn(new OperatorAccess.Operator(UUID.randomUUID(),OperatorAccess.Role.REVIEWER));
        assertThatThrownBy(() -> appeals.list("reviewer","verified","Review appeals",0,UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
    }

    @Test void wrongRestrictionOwnershipAndUnverifiedEmailNeverSendAuthority() {
        Fixture owner=fixture(true), stranger=fixture(true), unverified=fixture(false);
        appeals.request(stranger.email(),owner.restriction());
        appeals.request(unverified.email(),unverified.restriction());
        appeals.request("missing@appeal.test",owner.restriction());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_email_outbox WHERE recipient IN (?,?,?)",Integer.class,
                owner.email(),stranger.email(),unverified.email())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_appeal_tokens WHERE restriction_id=?",Integer.class,owner.restriction())).isZero();
    }

    @Test void verifiedEmailTokenRecordsAnAppealOnceWithoutIssuingASession() {
        Fixture owner=fixture(true);
        appeals.request(owner.email(),owner.restriction());
        String token=sentToken(owner.email());
        appeals.submit(token,"Please review the restriction",UUID.randomUUID());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_appeals WHERE restriction_id=? AND user_id=?",Integer.class,owner.restriction(),owner.user())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id=?",Integer.class,owner.user())).isZero();
        assertThatThrownBy(() -> appeals.submit(token,"Replay",UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
    }

    @Test void expiredTokenCannotCreateAnAppeal() {
        Fixture owner=fixture(true);
        appeals.request(owner.email(),owner.restriction());
        String token=sentToken(owner.email());
        jdbc.update("UPDATE moderation_appeal_tokens SET expires_at=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) WHERE token_hash=?",OperatorAccess.hash(token));
        assertThatThrownBy(() -> appeals.submit(token,"Review expired link",UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_appeals WHERE restriction_id=?",Integer.class,owner.restriction())).isZero();
        appeals.request(owner.email(),owner.restriction());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_appeal_tokens WHERE token_hash=?",Integer.class,OperatorAccess.hash(token))).isZero();
    }

    private String sentToken(String recipient) {
        String body=jdbc.queryForObject("SELECT body_text FROM auth_email_outbox WHERE recipient=?",String.class,recipient);
        return body.split("#token=",2)[1].strip();
    }

    private Fixture fixture(boolean verified) {
        UUID user=UUID.randomUUID(),report=UUID.randomUUID(),restriction=UUID.randomUUID();
        String address=user+"@appeal.test";
        // The provider account's random unusable password is deliberately irrelevant to verified email appeals.
        jdbc.update("INSERT INTO auth_users(id,email,password_hash,display_name,email_verified,created_at) VALUES(?,?,?,'Provider user',?,CURRENT_TIMESTAMP)",user,address,UUID.randomUUID().toString(),verified);
        jdbc.update("INSERT INTO moderation_reports(id,reporter_id,target_type,target_id,reason,evidence,status,created_at,updated_at) VALUES(?,?,'USER',?,'Report','{}','NEW',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",report,UUID.randomUUID(),user);
        jdbc.update("INSERT INTO moderation_restrictions(id,report_id,target_type,target_id,actor_id,reason,starts_at,expires_at) VALUES(?,?,'USER',?,?,'Reviewable action',CURRENT_TIMESTAMP,DATEADD('DAY',1,CURRENT_TIMESTAMP))",restriction,report,user,UUID.randomUUID());
        return new Fixture(user,address,restriction);
    }
    private record Fixture(UUID user,String email,UUID restriction) { }
}
