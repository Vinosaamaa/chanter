package com.chanter.agent.application;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Answer capability is independent from the provider's API or future native-companion access method. */
public final class AiAnswerMode {
    private AiAnswerMode() {}
    public static String resolve(String requested, String modelId) {
        String mode = requested == null || requested.isBlank()
                ? (LlmModelCatalog.SOURCE_ONLY.equals(modelId) ? "source-only" : "quoted-evidence") : requested;
        if ("grounded-explanation".equals(mode)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Grounded explanations are unavailable until retrieval and grounding evaluations are verified");
        if (!List.of("source-only", "quoted-evidence").contains(mode)
                || ("quoted-evidence".equals(mode) && LlmModelCatalog.SOURCE_ONLY.equals(modelId)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer mode is not supported by this selection");
        return mode;
    }
    public static List<Capability> capabilities(boolean hasGenerationModel) {
        return List.of(new Capability("source-only", "Approved sources", true, null),
                new Capability("quoted-evidence", "Relevant source quotations", hasGenerationModel,
                        hasGenerationModel ? null : "generation-model-unavailable"),
                new Capability("grounded-explanation", "Grounded study explanation", false, "grounding-evaluation-pending"));
    }
    public record Capability(String id, String label, boolean available, String unavailableReason) {}
}
