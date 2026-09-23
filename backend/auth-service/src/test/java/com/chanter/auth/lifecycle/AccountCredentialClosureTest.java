package com.chanter.auth.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.chanter.auth.application.*;
import com.chanter.auth.domain.AuthUser;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
        "spring.datasource.url=jdbc:h2:mem:credential-closure;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "chanter.events.dispatch-enabled=false","chanter.auth.email-delivery-enabled=false"})
@ActiveProfiles("test")
class AccountCredentialClosureTest {
    @Autowired AuthSessionService sessions;
    @Autowired AuthUserRepository users;
    @Autowired AuthEmailTokenRepository tokens;
    @Autowired OAuthAccountRepository oauth;
    @Autowired ProductionAuthService production;
    @Autowired RefreshTokenRepository refresh;
    @Autowired TerminalJournalStore journal;
    @Autowired AuthTerminalRecovery recovery;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;

    @Test void staleAccountObjectsCannotRecreateTokensProfileOrOauthLinksAfterClosure() {
        var user=account();
        oauth.link(UUID.randomUUID(),user.id(),"google","subject-"+user.id());
        jdbc.update("INSERT INTO platform_operators(user_id,role,granted_at,factor_ciphertext,factor_confirmed) VALUES (?,'ADMIN',CURRENT_TIMESTAMP,'synthetic-encrypted-factor',TRUE)",user.id());
        jdbc.update("INSERT INTO platform_step_up(token_hash,user_id,access_token_hash,expires_at) VALUES (?,?,?,DATEADD('MINUTE',5,CURRENT_TIMESTAMP))","credential-"+user.id(),user.id(),"access-hash");
        production.sendEmailVerification(user);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_email_outbox WHERE recipient=?",Integer.class,user.email())).isPositive();
        new TransactionTemplate(manager).executeWithoutResult(status -> recovery.applyCommitted(journal.append("ACCOUNT",user.id())));
        production.sendEmailVerification(user);
        production.notifyExistingAccountRegisterAttempt(user);
        production.requestPasswordReset(user.email());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_email_outbox WHERE recipient=?",Integer.class,user.email())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_email_tokens WHERE user_id=? AND used_at IS NULL",Integer.class,user.id())).isZero();
        assertThatThrownBy(() -> users.update(user)).hasMessageContaining("410");
        assertThatThrownBy(() -> users.updatePasswordHash(user.id(),"replacement")).hasMessageContaining("410");
        assertThatThrownBy(() -> users.markEmailVerified(user.id())).hasMessageContaining("410");
        assertThatThrownBy(() -> oauth.link(UUID.randomUUID(),user.id(),"google","synthetic-subject")).hasMessageContaining("410");
        assertThatThrownBy(() -> tokens.save(UUID.randomUUID(),user.id(),UUID.randomUUID().toString(),"PASSWORD_RESET",Instant.now().plusSeconds(300))).hasMessageContaining("410");
        for(String table:java.util.List.of("auth_sessions","auth_refresh_tokens","auth_oauth_accounts","auth_email_tokens","platform_step_up","moderation_appeal_tokens"))
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE user_id=?",Integer.class,user.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT factor_ciphertext IS NULL AND revoked_at IS NOT NULL AND factor_confirmed=FALSE FROM platform_operators WHERE user_id=?",Boolean.class,user.id())).isTrue();
        assertThat(users.findById(user.id()).orElseThrow()).extracting(AuthUser::email,AuthUser::passwordHash,AuthUser::displayName)
                .containsExactly("deleted:"+user.id(),"!deleted","Deleted account");
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new com.chanter.auth.api.RegisterRequest("deleted:"+user.id(),"synthetic password","Collision")))
                    .anyMatch(violation -> violation.getPropertyPath().toString().equals("email"));
        }
    }

    @Test void operatorWriteCommitsBeforeDeletionErasesFactorsWithoutUserOperatorLockInversion() throws Exception {
        var user=account();
        var locked=new CountDownLatch(1); var finish=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var operator=executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                recovery.lockOperatorAuthority(); locked.countDown(); await(finish);
                assertThat(users.lockActive(user.id())).isTrue();
                jdbc.update("INSERT INTO platform_operators(user_id,role,granted_at,factor_ciphertext) VALUES (?,'REVIEWER',CURRENT_TIMESTAMP,'late-factor')",user.id());
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var closing=executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status ->
                    recovery.applyCommitted(journal.append("ACCOUNT",user.id()))));
            try { assertThatThrownBy(() -> closing.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
            finally { finish.countDown(); }
            operator.get(5,TimeUnit.SECONDS); closing.get(5,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT factor_ciphertext IS NULL AND revoked_at IS NOT NULL FROM platform_operators WHERE user_id=?",Boolean.class,user.id())).isTrue();
        } finally { finish.countDown(); }
    }

    @Test void redemptionWaitsBehindUserLockAndCannotResurrectAfterConcurrentTerminalCommit() throws Exception {
        var user=account();
        String hash=UUID.randomUUID().toString();
        tokens.save(UUID.randomUUID(),user.id(),hash,"PASSWORD_RESET",Instant.now().plusSeconds(300));
        var locked=new CountDownLatch(1); var finish=new CountDownLatch(1); var started=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var closing=executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                var entry=journal.append("ACCOUNT",user.id());
                recovery.lockOperatorAuthority();
                refresh.lockUser(user.id()); locked.countDown(); await(finish);
                recovery.applyCommitted(entry);
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var redeem=executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                started.countDown(); return tokens.findActiveByTokenHash(hash,"PASSWORD_RESET",Instant.now());
            }));
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            try { assertThatThrownBy(() -> redeem.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
            finally { finish.countDown(); }
            closing.get(5,TimeUnit.SECONDS);
            assertThat(redeem.get(5,TimeUnit.SECONDS)).isEmpty();
        } finally { finish.countDown(); }
    }

    private AuthUser account() {
        var session=sessions.registerWithStatus("closure-"+UUID.randomUUID()+"@example.test","synthetic long password 251","Fixture","test").session();
        return users.findById(session.user().id()).orElseThrow();
    }
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Fixture latch timeout"); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
    }
}
