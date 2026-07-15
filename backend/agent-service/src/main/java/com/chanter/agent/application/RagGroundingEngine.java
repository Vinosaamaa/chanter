package com.chanter.agent.application;

import com.chanter.agent.application.GroundingEngine.GroundingResult;
import com.chanter.agent.application.GroundingEngine.GroundingSource;
import com.chanter.agent.application.GroundingEngine.SourceCitation;
import com.chanter.agent.application.VectorRetrievalService.RankedChunk;
import com.chanter.agent.domain.AnswerConfidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RAG grounding over #95 vector retrieval, with optional FAQ keyword supplemental scoring.
 * Citations include resource titles and character offsets from retrieved chunks.
 */
@Component
@ConditionalOnProperty(name = "chanter.grounding.engine", havingValue = "rag", matchIfMissing = true)
public class RagGroundingEngine {

    private final KeywordGroundingEngine keywordFallback;
    private final double minScore;

    public RagGroundingEngine(
            @Value("${chanter.grounding.min-retrieval-score:0.12}") double minScore
    ) {
        this.keywordFallback = new KeywordGroundingEngine();
        this.minScore = minScore;
    }

    public GroundingResult answer(
            String question,
            List<RankedChunk> rankedChunks,
            List<GroundingSource> faqSources,
            Map<UUID, String> resourceTitles
    ) {
        RankedChunk best = null;
        for (RankedChunk chunk : rankedChunks) {
            if (best == null || chunk.score() > best.score()) {
                best = chunk;
            }
        }

        if (best != null && best.score() >= minScore && best.contentText() != null && !best.contentText().isBlank()) {
            String title = resourceTitles.getOrDefault(best.resourceId(), best.fileName());
            String excerpt = excerptWithOffsets(best);
            String answerBody = "Based on \"" + title + "\" (chars " + best.startOffset()
                    + "-" + best.endOffset() + "): " + truncate(best.contentText(), 400);
            return new GroundingResult(
                    answerBody,
                    AnswerConfidence.HIGH,
                    List.of(new SourceCitation(best.resourceId(), title, excerpt))
            );
        }

        if (faqSources != null && !faqSources.isEmpty()) {
            GroundingResult faqResult = keywordFallback.answer(question, faqSources);
            if (faqResult.confidence() == AnswerConfidence.HIGH) {
                return faqResult;
            }
        }

        return lowConfidenceResult();
    }

    /**
     * When vector store is empty, score downloaded resource text with the keyword engine.
     */
    public GroundingResult answerWithKeywordFallback(String question, List<GroundingSource> sources) {
        return keywordFallback.answer(question, sources);
    }

    private static String excerptWithOffsets(RankedChunk chunk) {
        String body = truncate(chunk.contentText(), 240);
        return "[offsets " + chunk.startOffset() + "-" + chunk.endOffset() + "] " + body;
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars).trim() + "…";
    }

    private static GroundingResult lowConfidenceResult() {
        return new GroundingResult(
                "I do not have enough approved material to answer confidently. Please ask a TA or instructor for help.",
                AnswerConfidence.LOW,
                List.of()
        );
    }
}
