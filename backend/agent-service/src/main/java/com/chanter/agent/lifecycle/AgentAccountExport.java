package com.chanter.agent.lifecycle;

import com.chanter.agent.application.GroundedSupportQuestionService;
import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.common.lifecycle.AccountExportProjection;
import com.chanter.common.lifecycle.ExportSnapshotAccess;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.chanter.common.lifecycle.JdbcExportRows;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Saved answers retain their ordinary live grant/evidence boundary, including on a later export download. */
@Component
public class AgentAccountExport implements AccountExportProjection, ExportSnapshotAccess {
    private final JdbcTemplate jdbc;
    private final GroundedSupportQuestionService answers;
    private final ObjectMapper mapper;
    public AgentAccountExport(JdbcTemplate jdbc, GroundedSupportQuestionService answers, ObjectMapper mapper) {
        this.jdbc = jdbc; this.answers = answers; this.mapper = mapper;
    }
    @Override public void capture(UUID account, ExportSnapshotStore.Capture output) throws IOException {
        rows(output, "own_questions", "SELECT id,support_question_id,channel_id,study_server_id,learner_user_id,question_body,created_at FROM study_assistant_answers WHERE learner_user_id=? ORDER BY created_at,id", account);
        rows(output, "own_feedback", "SELECT answer_id,user_id,created_at FROM study_assistant_answer_helpful WHERE user_id=? ORDER BY created_at,answer_id", account);
        rows(output, "installation_metadata", "SELECT id,study_server_id,installed_by_user_id,installed_at FROM study_assistant_installs WHERE installed_by_user_id=? ORDER BY installed_at,id", account);
        rows(output, "generation_usage", """
            SELECT u.id,u.study_server_id,u.support_question_id,u.learner_user_id,u.selection_id,u.provider,u.requested_model,u.resolved_model,
                u.reserved_tokens,u.input_tokens,u.output_tokens,u.cache_read_tokens,u.cache_write_tokens,u.reasoning_tokens,u.measured,u.outcome,
                u.latency_ms,u.estimated_cost_usd,u.price_version,u.created_at,u.settled_at,
                CASE WHEN u.measured THEN 'MEASURED' ELSE 'UNKNOWN' END AS token_usage_status,
                CASE WHEN n.id IS NOT NULL THEN 'CLIENT_REPORTED' ELSE 'SERVER_RECORDED_ATTEMPT' END AS execution_record
            FROM ai_generation_usage u LEFT JOIN native_companion_requests n ON n.id=u.id
            WHERE u.learner_user_id=? ORDER BY u.created_at,u.id
            """, account);
        rows(output, "native_request_metadata", "SELECT id,channel_id,question_id,user_id,model,outcome,accept_until,provenance FROM native_companion_requests WHERE user_id=? ORDER BY accept_until,id", account);
        var omitted = new java.util.ArrayList<Map<String,Object>>();
        UUID after = new UUID(0, 0);
        while (true) {
            output.checkBudget();
            var ids = jdbc.query("SELECT id FROM study_assistant_answers WHERE learner_user_id=? AND id>? ORDER BY id LIMIT 100",
                    (rs, row) -> rs.getObject(1, UUID.class), account, after);
            if (ids.isEmpty()) break;
            for (UUID id : ids) {
                output.checkBudget();
                try {
                    var answer = current(account, id);
                    output.protectedJsonLines("answers", id, new ExportSnapshotStore.AccessScope("AI_ANSWER", id, digest(answer)),
                            records -> records.add(answer));
                } catch (ResponseStatusException denied) {
                    if (!java.util.Set.of(403,404,410).contains(denied.getStatusCode().value())) throw denied;
                    if (omitted.size() >= 10_000) throw new ExportSnapshotStore.ExportFailure("EXPORT_PROTECTED_ITEM_LIMIT", null);
                    omitted.add(Map.of("answerId", id, "reason", "CURRENT_ANSWER_OR_EVIDENCE_ACCESS_UNAVAILABLE"));
                }
            }
            after = ids.getLast();
        }
        output.jsonLines("unavailable_answers", rows -> { for (var row : omitted) rows.add(row); });
        output.jsonLines("coverage", records -> records.add(Map.of("source", "agent", "answers", "Only saved answers with current channel, question and source evidence access are included.",
                "provenance", "Desktop execution is client-reported and its token usage is UNKNOWN. Reserved tokens are accounting reservations, not observed provider usage.",
                "omissions", List.of("Provider credentials, live session identifiers, raw native evidence snapshots and other learners' prompts are excluded.",
                "Installation metadata does not grant access to the installation's current configuration or resources."))));
    }

    @Override public void require(UUID account, ExportSnapshotStore.AccessScope scope) {
        if (!scope.kind().equals("AI_ANSWER") || scope.expectedDigest() == null) throw denied();
        if (!digest(current(account, scope.id())).equals(scope.expectedDigest())) throw denied();
    }
    private StudyAssistantAnswer current(UUID account, UUID id) {
        var rows = jdbc.query("SELECT channel_id,support_question_id FROM study_assistant_answers WHERE id=? AND learner_user_id=?",
                (rs, row) -> new UUID[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)}, id, account);
        if (rows.size() != 1) throw denied();
        var answer = answers.findAnswer(rows.getFirst()[0], rows.getFirst()[1], account);
        if (!answer.id().equals(id) || !answer.learnerUserId().equals(account)) throw denied();
        return answer;
    }
    private String digest(StudyAssistantAnswer answer) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(answer))); }
        catch (IOException failure) { throw new ExportSnapshotStore.ExportFailure("EXPORT_SERIALIZATION_FAILED", failure); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private void rows(ExportSnapshotStore.Capture output, String section, String sql, Object... parameters) throws IOException {
        JdbcExportRows.write(jdbc, output, section, sql, parameters);
    }
    private static ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "EXPORT_ANSWER_ACCESS_REVOKED"); }
}
