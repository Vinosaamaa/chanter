package com.chanter.agent.application;

import com.chanter.agent.domain.AnswerConfidence;
import java.util.List;
import java.util.UUID;

public interface GroundingEngine {

    GroundingResult answer(String question, List<GroundingSource> sources);

    record GroundingSource(UUID resourceId, String title, String textContent) {
    }

    record SourceCitation(UUID resourceId, String resourceTitle, String excerpt) {
    }

    record GroundingResult(
            String answerBody,
            AnswerConfidence confidence,
            List<SourceCitation> citations,
            boolean handoffRecommended
    ) {
    }
}
