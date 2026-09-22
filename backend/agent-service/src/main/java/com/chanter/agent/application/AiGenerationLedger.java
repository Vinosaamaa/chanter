package com.chanter.agent.application;

import com.chanter.agent.config.LlmProperties.Model;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Reservations commit before network calls. Unmeasured work continues to consume its reserved budget. */
@Service
public class AiGenerationLedger {
    private final JdbcClient jdbc;
    private final Clock clock;
    private final LlmModelCatalog catalog;
    private final AiOperationalMetrics metrics;
    public AiGenerationLedger(JdbcClient jdbc, Clock clock, LlmModelCatalog catalog, AiOperationalMetrics metrics) {
        this.jdbc = jdbc; this.clock = clock; this.catalog = catalog; this.metrics = metrics;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID reserve(UUID server, UUID question, UUID user, String selection, Model model) {
        var authority=new com.chanter.agent.lifecycle.AgentLifecycleAccess(jdbc);
        authority.require("ACCOUNT",user); authority.require("STUDY_SERVER",server);
        // One existing installation row serializes all reservations in this Study Server, on PostgreSQL and H2.
        jdbc.sql("SELECT id FROM study_assistant_installs WHERE study_server_id=:server FOR UPDATE")
                .param("server", server).query(UUID.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "AI Study Assistant is not installed"));
        OffsetDateTime now = now();
        jdbc.sql("UPDATE ai_generation_usage SET outcome='UNKNOWN', settled_at=:now WHERE study_server_id=:server AND outcome='RESERVED' AND created_at<:stale")
                .param("now", now).param("server", server).param("stale", now.minusSeconds(130)).update();
        int active = jdbc.sql("SELECT COUNT(*) FROM ai_generation_usage WHERE study_server_id=:server AND support_question_id=:question AND outcome<>'NOT_STARTED'")
                .param("server", server).param("question", question).query(Integer.class).single();
        // Settlement precedes answer persistence. Every possible provider attempt retains this claim forever,
        // including abandoned reservations: a process crash cannot prove that no provider call was made.
        if (active > 0) throw new AttemptConflict();
        long reserved = Math.addExact(model.maxInputTokens(), model.maxOutputTokens());
        if (reserved > catalog.dailyTokenLimit() - summary(server).accountedTokens()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "The Study Server's daily AI token budget is exhausted");
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO ai_generation_usage (id,study_server_id,support_question_id,learner_user_id,selection_id,
                    provider,requested_model,reserved_tokens,outcome,created_at,price_version)
                VALUES (:id,:server,:question,:user,:selection,:provider,:model,:reserved,'RESERVED',:now,:price)
                """)
                .param("id", id).param("server", server).param("question", question).param("user", user)
                .param("selection", selection).param("provider", model.provider()).param("model", model.model())
                .param("reserved", reserved).param("now", now).param("price", model.price() == null ? null : model.price().version()).update();
        metrics.reserved();
        return id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settle(UUID reservation, LlmUsage usage, String outcome, long latencyMs, String resolvedModel,
                       String requestId, Model model, boolean attempted) {
        if (!Set.of("SUCCESS", "UNAVAILABLE", "RATE_LIMITED", "TIMED_OUT", "CANCELLED", "INVALID_RESPONSE", "REFUSED",
                "LIMIT_EXCEEDED", "REJECTED_EVIDENCE", "UNSUPPORTED", "UNKNOWN").contains(outcome))
            throw new IllegalArgumentException("Invalid AI outcome");
        LlmUsage measured = attempted ? (usage == null ? LlmUsage.UNKNOWN : usage) : new LlmUsage(0, 0, 0, 0, 0);
        // A late provider receipt may reconcile UNKNOWN after a process stall, but cannot overwrite a settled receipt.
        int changed = jdbc.sql("""
                UPDATE ai_generation_usage SET input_tokens=:input, output_tokens=:output, cache_read_tokens=:cacheRead,
                    cache_write_tokens=:cacheWrite, reasoning_tokens=:reasoning, measured=:measured, outcome=:outcome,
                    latency_ms=:latency, resolved_model=:resolved, provider_request_id=:request, estimated_cost_usd=:cost, settled_at=:now
                WHERE id=:id AND outcome IN ('RESERVED','UNKNOWN')
                """)
                .param("input", measured.inputTokens()).param("output", measured.outputTokens())
                .param("cacheRead", measured.cacheReadTokens()).param("cacheWrite", measured.cacheWriteTokens())
                .param("reasoning", measured.reasoningTokens()).param("measured", measured.measured()).param("outcome", attempted ? outcome : "NOT_STARTED")
                .param("latency", Math.max(0, latencyMs)).param("resolved", safeId(resolvedModel)).param("request", safeId(requestId))
                .param("cost", cost(measured, model, resolvedModel)).param("now", now()).param("id", reservation).update();
        if (changed == 1) metrics.settled(attempted ? outcome : "NOT_STARTED", measured.measured(), attempted, Math.max(0, latencyMs));
    }

    @Transactional(readOnly = true)
    public UsageSummary summary(UUID server) {
        OffsetDateTime start = now().toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        return jdbc.sql("""
                SELECT COALESCE(SUM(CASE WHEN measured THEN CAST(input_tokens AS BIGINT)+output_tokens ELSE reserved_tokens END),0) AS accounted,
                    COUNT(*) AS requests, COALESCE(SUM(CASE WHEN NOT measured THEN 1 ELSE 0 END),0) AS unknown_count,
                    COALESCE(SUM(CASE WHEN outcome='RESERVED' THEN 1 ELSE 0 END),0) AS pending_count,
                    COALESCE(SUM(CASE WHEN outcome NOT IN ('SUCCESS','RESERVED') THEN 1 ELSE 0 END),0) AS error_count,
                    SUM(estimated_cost_usd) AS known_cost, COALESCE(SUM(CASE WHEN estimated_cost_usd IS NULL THEN 1 ELSE 0 END),0) AS unknown_cost
                FROM ai_generation_usage WHERE study_server_id=:server AND created_at>=:start AND created_at<:end
                """).param("server", server).param("start", start).param("end", start.plusDays(1))
                .query((rs, row) -> new UsageSummary(rs.getLong("accounted"), catalog.dailyTokenLimit(), rs.getLong("requests"),
                        rs.getLong("unknown_count"), rs.getLong("pending_count"), rs.getLong("error_count"),
                        rs.getBigDecimal("known_cost"), rs.getLong("unknown_cost"), start.plusDays(1))).single();
    }

    private static BigDecimal cost(LlmUsage usage, Model model, String resolved) {
        if ("ollama".equals(model.provider())) return BigDecimal.ZERO;
        if (!usage.measured() || model.price() == null || !model.model().equals(resolved) || usage.cacheReadTokens() == null) return null;
        int writes = "anthropic".equals(model.provider()) ? (usage.cacheWriteTokens() == null ? -1 : usage.cacheWriteTokens()) : 0;
        if (writes < 0 || (long)usage.cacheReadTokens() + writes > usage.inputTokens()) return null;
        var price = model.price();
        return price.input().multiply(BigDecimal.valueOf(usage.inputTokens() - usage.cacheReadTokens() - writes))
                .add(price.output().multiply(BigDecimal.valueOf(usage.outputTokens())))
                .add(price.cacheRead().multiply(BigDecimal.valueOf(usage.cacheReadTokens())))
                .add(price.cacheWrite().multiply(BigDecimal.valueOf(writes)))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
    }
    private static String safeId(String value) { return value != null && value.matches("[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,127}") ? value : null; }
    private OffsetDateTime now() { return clock.instant().atOffset(ZoneOffset.UTC); }
    public static final class AttemptConflict extends ResponseStatusException {
        public AttemptConflict() {
            super(HttpStatus.CONFLICT, "A provider request has already started for this question. If no saved answer is available, its result may be unknown. Choose Source only or ask an Instructor or TA; another provider request will not be started.");
        }
    }
    public record UsageSummary(long accountedTokens, long tokenLimit, long requestCount, long unknownUsageCount,
                               long pendingCount, long errorCount, BigDecimal knownEstimatedCostUsd,
                               long unknownCostCount, OffsetDateTime resetsAt) {}
}
