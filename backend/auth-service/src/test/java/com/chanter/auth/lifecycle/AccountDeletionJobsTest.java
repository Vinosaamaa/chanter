package com.chanter.auth.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.chanter.auth.application.AuthSessionService;
import com.chanter.common.lifecycle.*;
import com.chanter.common.events.*;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:account-deletion;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "chanter.events.dispatch-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountDeletionJobsTest {
    @Autowired AccountDeletionJobs jobs;
    @Autowired AuthSessionService sessions;
    @Autowired TerminalJournalStore journal;
    @Autowired AccountDeletionProtocol protocol;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Test void canonicalConfirmationRevocationAndCommandsRollBackTogetherThenReceiptSurvivesLogout() throws Exception {
        var user=account(); String authorization=bearer(user); UUID id=UUID.randomUUID();
        var prepared=jobs.prepare(authorization,id);
        assertThat(prepared.job().state()).isEqualTo("PREPARING");
        assertThatThrownBy(() -> jobs.confirm(authorization,id)).hasMessageContaining("DELETION_NOT_PREPARED");
        jobs.accept(receipt(id,user.user().id(),"community","PREPARED",1,null));
        jdbc.execute("ALTER TABLE durable_outbox ADD CONSTRAINT fixture_source_command_failure CHECK(NOT(destination='lifecycle-media' AND kind='LIFECYCLE_TERMINAL_DELETE'))");
        try { assertThatThrownBy(() -> jobs.confirm(authorization,id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
        finally { jdbc.execute("ALTER TABLE durable_outbox DROP CONSTRAINT fixture_source_command_failure"); }
        assertThat(jobs.get(authorization,id).state()).isEqualTo("PREPARED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_terminal_journal WHERE target_id=?",Integer.class,user.user().id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id=? AND revoked_at IS NULL",Integer.class,user.user().id())).isEqualTo(1);
        var confirmed=jobs.confirm(authorization,id);
        assertThat(confirmed.state()).isEqualTo("ERASING"); assertThat(confirmed.replicationPending()).isTrue(); assertThat(confirmed.parts()).hasSize(7);
        assertThatThrownBy(() -> jobs.get(authorization,id)).hasMessageContaining("LIFECYCLE_ACCOUNT_DELETED");
        assertThatThrownBy(() -> sessions.refresh(user.refreshToken())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jobs.receipt(id,prepared.handle()).state()).isEqualTo("ERASING");
        assertThatThrownBy(() -> jobs.receipt(UUID.randomUUID(),prepared.handle())).hasMessageContaining("404");
        assertThat(jdbc.queryForObject("SELECT receipt_hash FROM lifecycle_deletion_jobs WHERE id=?",String.class,id)).hasSize(64).isNotEqualTo(prepared.handle());
        var entry=journal.page(0,null,256).entries().stream().filter(value -> value.targetId().equals(user.user().id())).findFirst().orElseThrow();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind=? AND aggregate_key=?",Integer.class,
                AccountDeletionProtocol.TERMINAL,AccountDeletionProtocol.key("ACCOUNT",user.user().id()))).isEqualTo(6);
        for(String source:AccountExportJobs.SOURCES) if(!source.equals("auth")) {
            var event=receipt(id,user.user().id(),source,"COMPLETE",2,entry); jobs.accept(event); jobs.accept(event);
        }
        journal.acknowledge(new TerminalJournal.Checkpoint(entry.revision(),entry.digest(),UUID.randomUUID()));
        assertThat(jobs.receipt(id,prepared.handle()).state()).isEqualTo("COMPLETE");
        assertThat(jobs.receipt(id,prepared.handle()).replicationPending()).isFalse();
        var cookie=new Cookie(AccountDeletionJobs.COOKIE,prepared.handle());
        mvc.perform(get(AccountDeletionJobs.path(id)).cookie(cookie)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/account/deletions/"+id+"/confirm").cookie(cookie)
                .header("Origin","http://localhost:5173").header("X-Chanter-CSRF","1").contentType("application/json")
                .content("{\"confirmation\":\"DELETE MY ACCOUNT\"}")).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE lifecycle_deletion_jobs SET receipt_expires_at=? WHERE id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),id);
        mvc.perform(get(AccountDeletionJobs.path(id)).cookie(cookie)).andExpect(status().isNotFound());
    }
    @Test void ownershipBlockedCancellationWaitsForReleaseAndCannotBeReopenedByStalePreparation() {
        var user=account(); String authorization=bearer(user); UUID id=UUID.randomUUID();
        jobs.prepare(authorization,id); jobs.accept(receipt(id,user.user().id(),"community","BLOCKED_OWNERSHIP",1,null));
        assertThat(jobs.get(authorization,id).state()).isEqualTo("BLOCKED_OWNERSHIP");
        assertThatThrownBy(() -> jobs.confirm(authorization,id)).hasMessageContaining("DELETION_NOT_PREPARED");
        assertThat(jobs.cancel(authorization,id).state()).isEqualTo("CANCELLING");
        assertThatThrownBy(() -> jobs.prepare(authorization,UUID.randomUUID())).hasMessageContaining("DELETION_ALREADY_ACTIVE");
        jobs.accept(receipt(id,user.user().id(),"community","PREPARED",2,null));
        assertThat(jobs.get(authorization,id).state()).isEqualTo("CANCELLING");
        jobs.accept(receipt(id,user.user().id(),"community","RELEASED",3,null));
        assertThat(jobs.get(authorization,id).state()).isEqualTo("CANCELLED");
        jobs.accept(receipt(id,user.user().id(),"community","PREPARED",4,null));
        assertThat(jobs.get(authorization,id).state()).isEqualTo("CANCELLED");
        assertThat(jobs.prepare(authorization,UUID.randomUUID()).job().state()).isEqualTo("PREPARING");
    }
    @Test void cookieIsExactReadOnlyAndRecentLoginCannotBeRenewedByRefresh() throws Exception {
        var user=account(); UUID id=UUID.randomUUID(); String body="{\"requestId\":\""+id+"\"}";
        String path="/api/v1/auth/account/deletions";
        mvc.perform(post(path).header("Authorization",bearer(user)).contentType("application/json").content(body)).andExpect(status().isForbidden());
        var response=mvc.perform(post(path).header("Authorization",bearer(user)).header("Origin","http://localhost:5173").header("X-Chanter-CSRF","1")
                .contentType("application/json").content(body)).andExpect(status().isAccepted()).andReturn().getResponse();
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly","Secure","SameSite=Strict","Path="+AccountDeletionJobs.path(id),"Max-Age=604800");
        assertThat(response.getContentAsString()).doesNotContain(user.user().id().toString(),user.user().email(),"handle");
        var other=account(); assertThatThrownBy(() -> jobs.prepare(bearer(other),id)).hasMessageContaining("404");
        jobs.accept(receipt(id,user.user().id(),"community","PREPARED",1,null));
        jdbc.update("UPDATE auth_sessions SET created_at=? WHERE user_id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(301)),user.user().id());
        var rotated=sessions.refresh(user.refreshToken());
        assertThatThrownBy(() -> jobs.confirm(bearer(rotated),id)).hasMessageContaining("RECENT_LOGIN_REQUIRED");
        jdbc.update("UPDATE lifecycle_deletion_jobs SET expires_at=? WHERE id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),id);
        assertThat(jobs.get(bearer(rotated),id).state()).isEqualTo("PREPARATION_EXPIRED");
        jobs.expirePreparations(); jobs.expirePreparations();
        assertThat(jobs.get(bearer(rotated),id).state()).isEqualTo("CANCELLING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind=? AND aggregate_key=?",Integer.class,AccountDeletionProtocol.RELEASE,
                AccountDeletionProtocol.key("ACCOUNT",user.user().id()))).isEqualTo(1);
    }
    private DurableEvent receipt(UUID job,UUID account,String source,String state,long revision,TerminalJournal.Entry terminal) {
        var receipt=new AccountDeletionProtocol.Receipt(job,source,"ACCOUNT",account,state,terminal==null ? null : terminal.revision(),terminal==null ? null : terminal.digest());
        return new DurableEvent(UUID.randomUUID(),1,source,revision,AccountDeletionProtocol.RECEIPT,AccountDeletionProtocol.key("ACCOUNT",account),protocol.encode(receipt));
    }
    private AuthSessionService.AuthSession account() { return sessions.registerWithStatus("deletion-"+UUID.randomUUID()+"@example.test","private fixture password 251","Synthetic","Fixture").session(); }
    private String bearer(AuthSessionService.AuthSession user) { return "Bearer "+user.accessToken(); }
}
