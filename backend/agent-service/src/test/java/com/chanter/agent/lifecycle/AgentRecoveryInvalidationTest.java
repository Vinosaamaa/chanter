package com.chanter.agent.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chanter.agent.infra.NativeRequestRepository;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.lifecycle.RecoveryInvalidationStore;
import com.chanter.common.lifecycle.TerminalJournal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;

class AgentRecoveryInvalidationTest {
    @Test void pendingNativeRequestsAndReceiptRollbackTogetherThenReplayCannotAcceptThemOrChangeUnknownUsage() throws Exception {
        var data=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(data).locations("classpath:db/migration")
                .placeholders(Map.of("testVectorDatabase","true")).load().migrate();
        var jdbc=new JdbcTemplate(data); var transactions=new DataSourceTransactionManager(data);
        var now=Instant.parse("2026-09-22T18:00:00Z"); var clock=Clock.fixed(now,ZoneOffset.UTC);
        var service=new AgentRecoveryInvalidation(jdbc,transactions,clock);
        var nativeRequests=new NativeRequestRepository(JdbcClient.create(jdbc),clock);
        var requests=new java.util.ArrayList<NativeRequestRepository.Request>();
        for(String outcome:List.of("ISSUED","ACCEPTING","ACCEPTED")) {
            UUID id=UUID.randomUUID(),user=UUID.randomUUID(),question=UUID.randomUUID(),server=UUID.randomUUID();
            jdbc.update("""
                INSERT INTO ai_generation_usage(id,study_server_id,support_question_id,learner_user_id,selection_id,provider,requested_model,reserved_tokens,measured,outcome,created_at)
                VALUES (?,?,?,?,'codex-subscription','codex-native','fixture-model',5000,FALSE,'RESERVED',?)
                """,id,server,question,user,Timestamp.from(now));
            String evidence=new ObjectMapper().writeValueAsString(new com.chanter.agent.application.GroundedSupportQuestionService.NativeEvidence(
                    server,UUID.randomUUID(),"private evidence",List.of()));
            var request=new NativeRequestRepository.Request(id,UUID.randomUUID(),question,user,UUID.randomUUID(),UUID.randomUUID(),
                    "fixture-model",evidence,"a".repeat(64),"b".repeat(64),now.plusSeconds(300));
            new TransactionTemplate(transactions).executeWithoutResult(status -> nativeRequests.issue(request));
            jdbc.update("UPDATE native_companion_requests SET outcome=?,evidence_json=? WHERE id=?",outcome,
                    outcome.equals("ACCEPTED")?null:"private evidence",id);
            requests.add(request);
        }
        var authority=new TerminalJournal.Watermark(0,TerminalJournal.GENESIS);
        var recovery=new RecoveryInvalidationStore.Request(UUID.randomUUID(),authority);
        new TransactionTemplate(transactions).executeWithoutResult(status -> { service.invalidate(recovery); status.setRollbackOnly(); });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM native_companion_requests WHERE evidence_json IS NOT NULL",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_recovery_invalidations",Integer.class)).isZero();
        var receipt=service.invalidate(recovery);
        assertThat(receipt.scope()).isEqualTo("ALL_PENDING_NATIVE_REQUESTS");
        assertThat(receipt.source()).isEqualTo("agent");
        assertThat(new AgentRecoveryInvalidation(jdbc,transactions,clock).invalidate(recovery)).isEqualTo(receipt);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM native_companion_requests WHERE evidence_json IS NOT NULL",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM native_companion_requests WHERE outcome='REJECTED'",Integer.class)).isEqualTo(2);
        for(var request:requests.subList(0,2)) {
            assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> nativeRequests.claim(request.id(),request.channel(),request.question(),request.user(),request.session(),request.installation())))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            nativeRequests.finish(request.id(),true,10,20);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM native_companion_requests WHERE outcome='ACCEPTED'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_generation_usage WHERE measured=FALSE AND input_tokens IS NULL AND output_tokens IS NULL AND reserved_tokens=5000",Integer.class)).isEqualTo(3);
        var mapper=new ObjectMapper().findAndRegisterModules();
        var controller=new AgentRecoveryInvalidationController(service,mapper,"private-agent-recovery-fixture-token");
        var http=MockMvcBuilders.standaloneSetup(controller).build();
        String route="/api/v1/internal/lifecycle/recovery/invalidate-sessions";
        http.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(recovery))).andExpect(status().isUnauthorized());
        http.perform(post(route).header(AuthHeaders.INTERNAL_SERVICE_TOKEN,"private-agent-recovery-fixture-token")
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(recovery)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.scope").value("ALL_PENDING_NATIVE_REQUESTS"));
        for(String invalid:List.of("null","{}","{\"recoveryId\":null,\"recoveryId\":null}"," ".repeat(2049)))
            http.perform(post(route).header(AuthHeaders.INTERNAL_SERVICE_TOKEN,"private-agent-recovery-fixture-token")
                    .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        var newer=new TerminalJournal.Watermark(1,"a".repeat(64));
        assertThatThrownBy(() -> service.invalidate(new RecoveryInvalidationStore.Request(UUID.randomUUID(),newer))).isInstanceOf(IllegalArgumentException.class);
        jdbc.update("UPDATE lifecycle_reapply_head SET revision=1,digest=? WHERE id=1",newer.digest());
        assertThatThrownBy(() -> service.invalidate(recovery)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.invalidate(new RecoveryInvalidationStore.Request(recovery.recoveryId(),newer))).isInstanceOf(IllegalArgumentException.class);
    }
}
