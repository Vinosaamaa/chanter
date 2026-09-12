package com.chanter.agent.application;

import com.chanter.agent.application.GroundingEngine.GroundingResult;
import com.chanter.agent.domain.AnswerConfidence;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntimeService {
    private final LlmModelCatalog catalog;
    private final AiGenerationLedger ledger;
    private final GroundedAnswerValidator validator;
    public AgentRuntimeService(LlmModelCatalog catalog, AiGenerationLedger ledger, GroundedAnswerValidator validator) {
        this.catalog = catalog; this.ledger = ledger; this.validator = validator;
    }

    /** Calls without an authenticated invocation can only use the free source fallback. */
    public OrchestratedAnswer orchestrate(String question, GroundingResult grounding) {
        return new OrchestratedAnswer(grounding, "disabled", "none", false);
    }

    public OrchestratedAnswer orchestrate(String question, GroundingResult grounding, Invocation invocation,
                                         LlmExecution execution, Consumer<String> chunks) {
        execution.check();
        invocation.reauthorize().run();
        if (LlmModelCatalog.SOURCE_ONLY.equals(invocation.modelId()) || grounding.confidence() != AnswerConfidence.HIGH || grounding.citations().isEmpty()) {
            chunks.accept(grounding.answerBody());
            return new OrchestratedAnswer(grounding, "disabled", "none", false);
        }
        var model = catalog.definition(invocation.modelId());
        String prompt;
        try {
            prompt = validator.prompt(question, grounding.citations());
            // UTF-8 byte length plus a framing allowance is conservative for the supported byte-token APIs.
            if ((long)prompt.getBytes(StandardCharsets.UTF_8).length + GroundedAnswerValidator.SYSTEM.getBytes(StandardCharsets.UTF_8).length + 256 > model.maxInputTokens())
                throw new LlmProviderException(LlmProviderException.Outcome.LIMIT_EXCEEDED);
        } catch (LlmProviderException rejected) {
            return handoff(model.provider(), model.model(), rejected.outcome());
        }
        UUID ticket = ledger.reserve(invocation.studyServerId(), invocation.questionId(), invocation.userId(), invocation.modelId(), model);
        long started = System.nanoTime();
        LlmUsage usage = new LlmUsage(0, 0, 0, 0, 0);
        String requestId = null;
        String resolved = model.model();
        String outcome = "UNKNOWN";
        boolean attempted = false;
        try (var providerExecution = execution.child(model.timeout())) {
            invocation.reauthorize().run();
            providerExecution.check();
            var stream = validator.stream(grounding.citations(), () -> { providerExecution.check(); invocation.reauthorize().run(); }, chunks);
            usage = LlmUsage.UNKNOWN;
            attempted = true;
            var response = catalog.client(invocation.modelId()).stream(
                    new LlmChatClient.LlmChatRequest(GroundedAnswerValidator.SYSTEM, prompt, model.maxOutputTokens()), providerExecution, stream);
            usage = response.usage(); requestId = response.requestId(); resolved = response.model();
            if (usage != null && ((usage.inputTokens() != null && usage.inputTokens() > model.maxInputTokens())
                    || (usage.outputTokens() != null && usage.outputTokens() > model.maxOutputTokens())))
                throw new LlmProviderException(LlmProviderException.Outcome.LIMIT_EXCEEDED);
            GroundingResult result = stream.finish();
            outcome = result.handoffRecommended() ? "UNSUPPORTED" : "SUCCESS";
            return new OrchestratedAnswer(result, model.provider(), resolved, true);
        } catch (LlmProviderException failure) {
            outcome = failure.outcome().name();
            if (failure.outcome() == LlmProviderException.Outcome.CANCELLED) throw failure;
            var handoff = handoff(model.provider(), resolved, failure.outcome());
            return new OrchestratedAnswer(handoff.result(), handoff.providerId(), handoff.modelId(), attempted);
        } catch (RuntimeException authorizationOrPersistenceFailure) {
            outcome = "REJECTED_EVIDENCE";
            throw authorizationOrPersistenceFailure;
        } finally {
            ledger.settle(ticket, usage, outcome, (System.nanoTime() - started) / 1_000_000, resolved, requestId, model);
        }
    }
    private static OrchestratedAnswer handoff(String provider, String model, LlmProviderException.Outcome outcome) {
        String reason = switch (outcome) {
            case UNAVAILABLE -> "The AI provider is unavailable.";
            case RATE_LIMITED -> "The AI provider's usage limit was reached.";
            case TIMED_OUT -> "The AI provider did not finish in time.";
            case LIMIT_EXCEEDED -> "This answer exceeded the configured AI limit.";
            case REFUSED -> "This request could not be answered safely.";
            default -> "I could not verify a supported answer.";
        };
        return new OrchestratedAnswer(new GroundingResult(reason + " An Instructor or TA can help with this question.", AnswerConfidence.LOW, List.of()), provider, model, false);
    }
    public record Invocation(UUID studyServerId, UUID questionId, UUID userId, String modelId, Runnable reauthorize) {}
    public record OrchestratedAnswer(GroundingResult result, String providerId, String modelId, boolean llmUsed) {}
}
