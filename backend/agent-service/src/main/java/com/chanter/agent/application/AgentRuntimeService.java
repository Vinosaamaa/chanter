package com.chanter.agent.application;

import com.chanter.agent.application.GroundingEngine.GroundingResult;
import com.chanter.agent.application.GroundingEngine.SourceCitation;
import com.chanter.agent.domain.AnswerConfidence;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates optional LLM refinement of RAG grounding (#98).
 * When {@code chanter.llm.enabled=false}, returns the grounding result unchanged.
 */
@Service
public class AgentRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeService.class);

    private final LlmChatClient llmChatClient;

    public AgentRuntimeService(LlmChatClient llmChatClient) {
        this.llmChatClient = llmChatClient;
    }

    public OrchestratedAnswer orchestrate(String question, GroundingResult grounding) {
        if (grounding.confidence() != AnswerConfidence.HIGH || grounding.citations().isEmpty()) {
            return new OrchestratedAnswer(grounding, llmChatClient.providerId(), llmChatClient.modelId(), false);
        }
        if (!llmChatClient.isEnabled()) {
            return new OrchestratedAnswer(grounding, "disabled", "none", false);
        }

        String system = """
                You are Chanter's AI Study Assistant. Answer using ONLY the provided course excerpts.
                Be concise. If the excerpts are insufficient, say you are not confident.
                Cite sources by title when relevant.
                """;
        String context = grounding.citations().stream()
                .map(c -> "Source: " + c.resourceTitle() + "\n" + c.excerpt())
                .collect(Collectors.joining("\n\n"));
        String user = "Question: " + question + "\n\nApproved excerpts:\n" + context;

        try {
            LlmChatClient.LlmChatResponse response = llmChatClient.complete(
                    new LlmChatClient.LlmChatRequest(system, user)
            );
            String content = response.content();
            if (content == null || content.isBlank()) {
                return new OrchestratedAnswer(grounding, llmChatClient.providerId(), llmChatClient.modelId(), false);
            }
            String answerBody = content.trim() + "\n\nSources: " + grounding.citations().stream()
                    .map(SourceCitation::resourceTitle)
                    .collect(Collectors.joining("; "));
            GroundingResult refined = new GroundingResult(
                    answerBody,
                    AnswerConfidence.HIGH,
                    grounding.citations()
            );
            log.info(
                    "Agent runtime used LLM provider={} model={} citationCount={}",
                    llmChatClient.providerId(),
                    llmChatClient.modelId(),
                    grounding.citations().size()
            );
            return new OrchestratedAnswer(refined, llmChatClient.providerId(), response.model(), true);
        } catch (RuntimeException exception) {
            log.warn("LLM orchestration failed; falling back to RAG answer: {}", exception.getMessage());
            return new OrchestratedAnswer(grounding, llmChatClient.providerId(), llmChatClient.modelId(), false);
        }
    }

    public record OrchestratedAnswer(
            GroundingResult result,
            String providerId,
            String modelId,
            boolean llmUsed
    ) {
    }
}
