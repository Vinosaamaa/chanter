package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanter.agent.application.GroundingEngine.GroundingSource;
import com.chanter.agent.application.VectorRetrievalService.RankedChunk;
import com.chanter.agent.domain.AnswerConfidence;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RagGroundingEngineTest {

    private final RagGroundingEngine engine = new RagGroundingEngine(0.12);

    @Test
    void highConfidenceUsesChunkOffsetsInCitation() {
        UUID resourceId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        RankedChunk chunk = new RankedChunk(
                UUID.randomUUID(),
                resourceId,
                courseId,
                0,
                10,
                80,
                "Configure HttpSecurity filters for Spring Security endpoints.",
                "guide.md",
                0.55,
                "hashing-v1-64"
        );

        var result = engine.answer(
                "How do I configure Spring Security filters?",
                List.of(chunk),
                List.of(),
                Map.of(resourceId, "Spring Security Guide")
        );

        assertThat(result.confidence()).isEqualTo(AnswerConfidence.HIGH);
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().getFirst().excerpt()).contains("offsets 10-80");
        assertThat(result.answerBody()).contains("chars 10-80");
    }

    @Test
    void weakScoreFallsBackToFaqThenLowConfidence() {
        UUID resourceId = UUID.randomUUID();
        RankedChunk weak = new RankedChunk(
                UUID.randomUUID(),
                resourceId,
                UUID.randomUUID(),
                0,
                0,
                20,
                "unrelated",
                "x.md",
                0.01,
                "hashing"
        );

        var low = engine.answer("What is the weather?", List.of(weak), List.of(), Map.of());
        assertThat(low.confidence()).isEqualTo(AnswerConfidence.LOW);
        assertThat(low.handoffRecommended()).isTrue();

        UUID faqId = UUID.randomUUID();
        var faqHigh = engine.answer(
                "How do I submit homework before the deadline?",
                List.of(weak),
                List.of(new GroundingSource(
                        faqId,
                        "FAQ: How do I submit homework before the deadline?",
                        "How do I submit homework before the deadline?\n\nUse the portal before Friday."
                )),
                Map.of()
        );
        assertThat(faqHigh.confidence()).isEqualTo(AnswerConfidence.HIGH);
        assertThat(faqHigh.citations().getFirst().resourceId()).isEqualTo(faqId);
    }
}
