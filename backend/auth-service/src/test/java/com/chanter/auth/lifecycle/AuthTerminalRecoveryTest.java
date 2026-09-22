package com.chanter.auth.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.chanter.auth.application.AuthSessionService;
import com.chanter.common.lifecycle.RecoveryInvalidationStore;
import com.chanter.common.lifecycle.TerminalJournal;
import com.chanter.common.lifecycle.TerminalReapplyStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties="spring.datasource.url=jdbc:h2:mem:auth-terminal-recovery;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class AuthTerminalRecoveryTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthSessionService sessions;
    @Autowired AccountExportJobs exports;
    @Autowired AuthTerminalRecovery recovery;
    @Autowired TerminalJournalStore journal;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AuthTerminalRecoveryController controller;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    @Test void privateRoutesRejectMissingCredentialsAmbiguousJsonAndInvalidScopeWithoutMutation() throws Exception {
        var head = recovery.receipt().authority();
        var request = new RecoveryInvalidationStore.Request(UUID.randomUUID(), head);
        String raw = mapper.writeValueAsString(request);
        String token = "test-internal-service-token-for-auth";
        assertThatThrownBy(() -> controller.invalidate(null,raw)).hasMessageContaining("401");
        assertThatThrownBy(() -> controller.receipt("spoofed-browser-token")).hasMessageContaining("401");
        assertThatThrownBy(() -> controller.reapply(null,"{}")).hasMessageContaining("401");
        assertThatThrownBy(() -> controller.invalidate(token,raw.substring(0,raw.length()-1) + ",\"unexpected\":true}")).hasMessageContaining("400");
        assertThatThrownBy(() -> controller.invalidate(token,raw.substring(0,raw.length()-1) + ",\"recoveryId\":\"" + UUID.randomUUID() + "\"}")).hasMessageContaining("400");
        assertThatThrownBy(() -> controller.invalidate(token,"null")).hasMessageContaining("400");
        assertThatThrownBy(() -> controller.invalidate(token," ".repeat(2049))).hasMessageContaining("400");
        assertThatThrownBy(() -> controller.reapply(token," ".repeat(256*1024+1))).hasMessageContaining("400");
        assertThatThrownBy(() -> controller.reapply(token,"{\"schemaVersion\":1,\"after\":null,\"through\":null,\"entries\":[],\"next\":null}")).hasMessageContaining("400");
        var response = controller.receipt(token);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().authority()).isEqualTo(head);
    }

    @Test void actualAccountEffectsCanonicalImportAndReceiptCommitTogetherThenAllRestoredCredentialsClose() {
        var account = sessions.registerWithStatus("owner-" + UUID.randomUUID() + "@example.test", "private fixture password 251", "Owner", "Synthetic test").session();
        var other = sessions.registerWithStatus("other-" + UUID.randomUUID() + "@example.test", "private fixture password 251", "Other", "Synthetic test").session();
        var job = exports.create("Bearer " + account.accessToken(), UUID.randomUUID());
        var otherJob = exports.create("Bearer " + other.accessToken(), UUID.randomUUID());
        var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        UUID emailToken = UUID.randomUUID();
        jdbc.update("INSERT INTO auth_email_tokens(id,user_id,token_hash,purpose,expires_at) VALUES (?,?,?,'PASSWORD_RESET',?)",
                emailToken, account.user().id(), UUID.randomUUID().toString(), Timestamp.from(now.plusSeconds(600)));
        UUID event = UUID.randomUUID();
        String digest = TerminalJournal.digest(1, event, "ACCOUNT", account.user().id(), now, TerminalJournal.GENESIS);
        var entry = new TerminalJournal.Entry(1,event,"ACCOUNT",account.user().id(),"DELETE",now,TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,digest);
        var zero = new TerminalJournal.Watermark(0,TerminalJournal.GENESIS);
        var head = new TerminalJournal.Watermark(1,digest);
        var page = new TerminalJournal.Page(TerminalJournal.SCHEMA_VERSION,zero,head,List.of(entry),head);
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> { recovery.reapply(page); status.setRollbackOnly(); });
        assertThat(journal.page(0,null,100).through()).isEqualTo(zero);
        assertThat(recovery.receipt().authority()).isEqualTo(zero);
        assertThat(exports.get("Bearer " + account.accessToken(),job.id()).state()).isEqualTo("BUILDING");
        assertThat(jdbc.queryForObject("SELECT used_at IS NULL FROM auth_email_tokens WHERE id=?",Boolean.class,emailToken)).isTrue();
        var receipt = recovery.reapply(page);
        assertThat(receipt).isEqualTo(new TerminalReapplyStore.Receipt(1,"auth",head,1,0));
        assertThat(recovery.reapply(page)).isEqualTo(receipt);
        assertThat(journal.page(0,null,100)).isEqualTo(page);
        assertThat(journal.checkpoint()).isNull();
        assertThatThrownBy(() -> sessions.requireActiveAccessSession("Bearer " + account.accessToken())).hasMessageContaining("401");
        assertThatThrownBy(() -> sessions.refresh(account.refreshToken())).hasMessageContaining("401");
        assertThatThrownBy(() -> sessions.login(account.user().email(),"private fixture password 251","Attempt after closure")).hasMessageContaining("401");
        assertThat(jdbc.queryForObject("SELECT state FROM lifecycle_export_jobs WHERE id=?",String.class,job.id())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_pages WHERE snapshot_id=?",Integer.class,job.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT used_at IS NOT NULL FROM auth_email_tokens WHERE id=?",Boolean.class,emailToken)).isTrue();
        assertThat(exports.get("Bearer " + other.accessToken(),otherJob.id()).state()).isEqualTo("BUILDING");
        var request = new RecoveryInvalidationStore.Request(UUID.randomUUID(),head);
        tx.executeWithoutResult(status -> { recovery.invalidate(request); status.setRollbackOnly(); });
        assertThat(sessions.requireActiveAccessSession("Bearer " + other.accessToken()).userId()).isEqualTo(other.user().id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_recovery_invalidations",Integer.class)).isZero();
        var invalidated = recovery.invalidate(request);
        assertThat(invalidated.scope()).isEqualTo("ALL_BROWSER_SESSIONS");
        assertThat(recovery.invalidate(request)).isEqualTo(invalidated);
        assertThatThrownBy(() -> sessions.requireActiveAccessSession("Bearer " + other.accessToken())).hasMessageContaining("401");
        assertThatThrownBy(() -> recovery.invalidate(new RecoveryInvalidationStore.Request(UUID.randomUUID(),zero))).hasMessageContaining("canonical head");
        tx.executeWithoutResult(status -> journal.append("RESOURCE",UUID.randomUUID()));
        assertThatThrownBy(() -> recovery.invalidate(request)).hasMessageContaining("canonical head");
    }
}
