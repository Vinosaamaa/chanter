package com.chanter.agent.application;

import com.chanter.agent.application.GroundedSupportQuestionService.NativeEvidence;
import com.chanter.agent.config.LlmProperties.Model;
import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.agent.infra.NativeRequestRepository;
import com.chanter.agent.infra.NativeSessionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Backend authority for native evidence release and validated client-result acceptance. */
@Service
public class NativeCompanionService {
    private final NativeCapabilitySigner signer;
    private final NativeSessionClient sessions;
    private final GroundedSupportQuestionService questions;
    private final GroundedAnswerValidator validator;
    private final AiGenerationLedger ledger;
    private final NativeRequestRepository requests;
    private static final ObjectMapper JSON = new ObjectMapper();
    public NativeCompanionService(NativeCapabilitySigner signer, NativeSessionClient sessions, GroundedSupportQuestionService questions,
            GroundedAnswerValidator validator, AiGenerationLedger ledger, NativeRequestRepository requests) {
        this.signer = signer; this.sessions = sessions; this.questions = questions; this.validator = validator; this.ledger = ledger; this.requests = requests;
    }
    public NativeCapabilitySigner.Ticket pair(UUID user, String authorization, String origin, UUID installation, boolean approved) {
        requireOrigin(origin); requireApproval(approved);
        return signer.pair(sessions.requireActive(authorization, user), installation);
    }
    public NativeCapabilitySigner.Ticket status(UUID user, String authorization, String origin, UUID installation) {
        requireOrigin(origin);
        return signer.status(sessions.requireActive(authorization, user), installation);
    }
    public Issued issue(UUID channel, UUID question, UUID user, String authorization, String origin, UUID installation, String model, boolean approved) {
        requireOrigin(origin); requireApproval(approved); signer.requireModel(model);
        var session = sessions.requireActive(authorization, user);
        try (var execution = new LlmExecution(Duration.ofSeconds(30))) {
            NativeEvidence evidence = questions.prepareNativeEvidence(channel, question, user, execution);
            String snapshot = encode(evidence);
            String prompt = GroundedAnswerValidator.SYSTEM + "\n" + validator.prompt(evidence.question(), evidence.citations());
            if (prompt.getBytes(StandardCharsets.UTF_8).length > NativeCapabilitySigner.MAX_INPUT_BYTES)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Approved evidence exceeds the native input bound");
            session = sessions.requireActive(authorization, user);
            questions.requireNativeEvidenceCurrent(channel, question, user, evidence, execution);
            UUID reservation = ledger.reserve(evidence.studyServerId(), question, user, "native-codex", definition(model));
            // From this point uncertainty retains the reservation; client claims cannot release or reduce it.
            var ticket = signer.study(session, installation, reservation, question, model, prompt, NativeCapabilitySigner.sha256(snapshot));
            requests.issue(new NativeRequestRepository.Request(reservation, channel, question, user, session.sessionId(), installation,
                    model, snapshot, NativeCapabilitySigner.sha256(prompt), NativeCapabilitySigner.sha256(snapshot), Instant.ofEpochMilli(ticket.expiresAt()).plusSeconds(30)));
            return new Issued(reservation, ticket.ticket(), ticket.expiresAt(), prompt, "quoted-evidence", "native-client-report");
        }
    }
    public StudyAssistantAnswer accept(UUID channel, UUID question, UUID requestId, UUID user, String authorization, String origin,
            UUID installation, String text, Integer inputTokens, Integer outputTokens) {
        requireOrigin(origin);
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > NativeCapabilitySigner.MAX_OUTPUT_BYTES
                || invalidUsage(inputTokens) || invalidUsage(outputTokens)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid native result bounds");
        var session = sessions.requireActive(authorization, user);
        var request = requests.claim(requestId, channel, question, user, session.sessionId(), installation);
        boolean accepted = false;
        String outcome = "UNKNOWN";
        try (var execution = new LlmExecution(Duration.ofSeconds(30))) {
            NativeEvidence evidence = decode(request.evidenceJson());
            if (!NativeCapabilitySigner.sha256(request.evidenceJson()).equals(request.evidenceHash()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Native evidence snapshot changed");
            Runnable reauthorize = () -> {
                var current = sessions.requireActive(authorization, user);
                if (!current.sessionId().equals(request.session())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Native session changed");
                questions.requireNativeEvidenceCurrent(channel, question, user, evidence, execution);
            };
            reauthorize.run();
            var stream = validator.stream(evidence.citations(), reauthorize, ignored -> {});
            stream.accept(text);
            var result = stream.finish();
            reauthorize.run();
            var answer = questions.acceptNativeAnswer(channel, question, user, evidence, result, request.model(), execution);
            accepted = true;
            outcome = "SUCCESS";
            return answer;
        } catch (LlmProviderException failure) {
            outcome = failure.outcome().name();
            throw switch (failure.outcome()) {
                case INVALID_RESPONSE -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Native result did not contain valid approved source quotations");
                case TIMED_OUT -> new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Native result validation timed out");
                case CANCELLED -> new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "Native result validation was cancelled");
                case REFUSED -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Native result declined to provide approved source quotations");
                case RATE_LIMITED, LIMIT_EXCEEDED -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Native result validation limit reached");
                case UNAVAILABLE -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Native result validation unavailable");
            };
        } finally {
            // Even a submitted zero is a client report, never measured provider usage or a budget refund.
            try { ledger.settle(requestId, LlmUsage.UNKNOWN, outcome, 0, request.model(), null, definition(request.model()), true); }
            finally { requests.finish(requestId, accepted, inputTokens, outputTokens); }
        }
    }
    private void requireOrigin(String origin) {
        if (!signer.available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Native companion is not configured");
        if (!signer.origin().equals(origin)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Native deployment origin mismatch");
    }
    private static void requireApproval(boolean approved) {
        if (!approved) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Explicit approval to export this request to Codex is required");
    }
    private static boolean invalidUsage(Integer value) { return value != null && (value < 0 || value > 1_000_000); }
    private static Model definition(String model) {
        return new Model("Native client", "codex-native", model, null, null, NativeCapabilitySigner.MAX_INPUT_BYTES,
                NativeCapabilitySigner.MAX_OUTPUT_BYTES, Duration.ofMillis(NativeCapabilitySigner.DEADLINE_MS), Set.of(), null);
    }
    private static String encode(NativeEvidence evidence) {
        try { return JSON.writeValueAsString(evidence); }
        catch (Exception failure) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Native evidence unavailable"); }
    }
    private static NativeEvidence decode(String evidence) {
        try { return JSON.readValue(evidence, NativeEvidence.class); }
        catch (Exception failure) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Native evidence unavailable"); }
    }
    public record Issued(UUID requestId, String ticket, long expiresAt, String prompt, String mode, String provenance) {}
}
