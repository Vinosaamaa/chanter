package com.chanter.agent.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chanter.agent.application.GroundedSupportQuestionService;
import com.chanter.agent.domain.AnswerConfidence;
import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.agent.domain.StudyAssistantAnswerSource;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class AgentAccountExportTest {
    @Test void nativeUsageStaysUnknownAndSavedAnswerPagesRepeatCurrentEvidenceAndContentChecks() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(data).locations("classpath:db/migration")
                .placeholders(java.util.Map.of("testVectorDatabase", "true")).load().migrate();
        var jdbc = new JdbcTemplate(data); var tx = new TransactionTemplate(new DataSourceTransactionManager(data));
        Instant now = Instant.parse("2026-09-19T00:00:00Z"); Timestamp time = Timestamp.from(now);
        UUID owner = UUID.randomUUID(); UUID other = UUID.randomUUID(); UUID id = UUID.randomUUID(); UUID question = UUID.randomUUID();
        UUID channel = UUID.randomUUID(); UUID server = UUID.randomUUID(); UUID session = UUID.randomUUID(); UUID installation = UUID.randomUUID(); UUID usage = UUID.randomUUID();
        jdbc.update("INSERT INTO study_assistant_answers VALUES (?,?,?,?,?,?,?,?,?,?)", id, question, channel, server, owner, "MY_QUESTION", "CURRENT_AUTHORIZED_ANSWER", "HIGH", false, time);
        jdbc.update("INSERT INTO study_assistant_answers VALUES (?,?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), UUID.randomUUID(), channel, server, other, "OTHER_PROMPT_CANARY", "OTHER_ANSWER_CANARY", "HIGH", false, time);
        jdbc.update("""
            INSERT INTO ai_generation_usage(id,study_server_id,support_question_id,learner_user_id,selection_id,provider,requested_model,reserved_tokens,measured,outcome,created_at)
            VALUES (?,?,?,?, 'codex-subscription','codex-native','fixture-model',5000,FALSE,'SUCCESS',?)
            """, usage, server, question, owner, time);
        jdbc.update("""
            INSERT INTO native_companion_requests(id,channel_id,question_id,user_id,session_id,installation_id,model,evidence_json,prompt_hash,evidence_hash,outcome,accept_until)
            VALUES (?,?,?,?,?,?,'fixture-model','PRIVATE_NATIVE_EVIDENCE_CANARY',?,?,'ACCEPTED',?)
            """, usage, channel, question, owner, session, installation, "a".repeat(64), "b".repeat(64), Timestamp.from(now.plusSeconds(300)));
        var grounded = mock(GroundedSupportQuestionService.class);
        var answer = new StudyAssistantAnswer(id, question, channel, server, owner, "MY_QUESTION", "CURRENT_AUTHORIZED_ANSWER", AnswerConfidence.HIGH, false,
                List.of(new StudyAssistantAnswerSource(UUID.randomUUID(), UUID.randomUUID(), "Authorized title", "AUTHORIZED_EXCERPT")), now);
        when(grounded.findAnswer(channel, question, owner)).thenReturn(answer);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var exporter = new AgentAccountExport(jdbc, grounded, mapper);
        var store = new ExportSnapshotStore(jdbc, tx, mapper, Clock.fixed(now, ZoneOffset.UTC), "agent");
        var request = new ExportSnapshotStore.Request(UUID.randomUUID(), owner, now, now.plusSeconds(86400));
        var manifest = store.capture(request, output -> exporter.capture(owner, output));
        var text = new StringBuilder();
        for (var entry : manifest.entries()) for (int page = 0; page < entry.pageCount(); page++)
            text.append(new String(store.page(request.jobId(), owner, entry.ordinal(), page), StandardCharsets.UTF_8));
        assertThat(text.toString()).contains("MY_QUESTION", "CURRENT_AUTHORIZED_ANSWER", "AUTHORIZED_EXCERPT", "CLIENT_REPORTED", "UNKNOWN", "\"input_tokens\":null")
                .doesNotContain("OTHER_PROMPT_CANARY", "OTHER_ANSWER_CANARY", "PRIVATE_NATIVE_EVIDENCE_CANARY", session.toString(), installation.toString());
        var protectedEntry = manifest.entries().stream().filter(entry -> entry.path().equals("answers/" + id + ".jsonl")).findFirst().orElseThrow();
        store.requireAccess(request.jobId(), owner, protectedEntry.ordinal(), exporter);
        verify(grounded, atLeast(2)).findAnswer(channel, question, owner);
        var revised = new StudyAssistantAnswer(id, question, channel, server, owner, "MY_QUESTION", "REDACTED_CURRENT_BODY", AnswerConfidence.HIGH, false, List.of(), now);
        when(grounded.findAnswer(channel, question, owner)).thenReturn(revised);
        assertThatThrownBy(() -> store.requireAccess(request.jobId(), owner, protectedEntry.ordinal(), exporter)).hasMessageContaining("EXPORT_ANSWER_ACCESS_REVOKED");
        when(grounded.findAnswer(channel, question, owner)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Evidence revoked"));
        assertThatThrownBy(() -> store.requireAccess(request.jobId(), owner, protectedEntry.ordinal(), exporter)).hasMessageContaining("Evidence revoked");
        store.cancelJob(request);
        var fresh = new ExportSnapshotStore.Request(UUID.randomUUID(), owner, now, now.plusSeconds(86400));
        var omitted = store.capture(fresh, output -> exporter.capture(owner, output));
        assertThat(omitted.entries()).noneMatch(entry -> entry.path().startsWith("answers/"));
        var omissions = omitted.entries().stream().filter(entry -> entry.path().equals("unavailable_answers.jsonl")).findFirst().orElseThrow();
        assertThat(new String(store.page(fresh.jobId(), owner, omissions.ordinal(), 0), StandardCharsets.UTF_8)).contains("CURRENT_ANSWER_OR_EVIDENCE_ACCESS_UNAVAILABLE");
    }
}
