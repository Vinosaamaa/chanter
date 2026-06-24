package com.chanter.agent.application;

import com.chanter.agent.domain.AnswerConfidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class KeywordGroundingEngine implements GroundingEngine {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "to", "in", "on", "for", "of", "is", "it", "i", "my", "how", "do"
    );

    @Override
    public GroundingResult answer(String question, List<GroundingSource> sources) {
        List<String> terms = tokenize(question);
        if (terms.isEmpty() || sources.isEmpty()) {
            return lowConfidenceResult();
        }

        GroundingSource bestSource = null;
        int bestScore = 0;
        String bestExcerpt = "";

        for (GroundingSource source : sources) {
            int score = scoreTerms(terms, source.textContent());
            if (score > bestScore) {
                bestScore = score;
                bestSource = source;
                bestExcerpt = excerpt(source.textContent(), terms);
            }
        }

        if (bestSource == null || bestScore < 2 || bestExcerpt.isBlank()) {
            return lowConfidenceResult();
        }

        String answerBody = "Based on \"" + bestSource.title() + "\": " + bestExcerpt;
        return new GroundingResult(
                answerBody,
                AnswerConfidence.HIGH,
                List.of(new SourceCitation(bestSource.resourceId(), bestSource.title(), bestExcerpt)),
                false
        );
    }

    private static GroundingResult lowConfidenceResult() {
        return new GroundingResult(
                "I do not have enough approved material to answer confidently. Please ask a TA or instructor for help.",
                AnswerConfidence.LOW,
                List.of(),
                true
        );
    }

    private static List<String> tokenize(String question) {
        String[] rawTerms = question.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        List<String> terms = new ArrayList<>();
        for (String term : rawTerms) {
            if (term.length() >= 3 && !STOP_WORDS.contains(term)) {
                terms.add(term);
            }
        }
        return terms;
    }

    private static int scoreTerms(List<String> terms, String textContent) {
        String normalized = textContent.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (normalized.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private static String excerpt(String textContent, List<String> terms) {
        String normalized = textContent.toLowerCase(Locale.ROOT);
        for (String term : terms) {
            int index = normalized.indexOf(term);
            if (index >= 0) {
                int start = Math.max(0, index - 40);
                int end = Math.min(textContent.length(), index + term.length() + 80);
                return textContent.substring(start, end).trim();
            }
        }
        return "";
    }
}
