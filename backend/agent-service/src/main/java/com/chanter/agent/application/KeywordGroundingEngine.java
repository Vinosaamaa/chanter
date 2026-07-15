package com.chanter.agent.application;

import com.chanter.agent.domain.AnswerConfidence;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "chanter.grounding.engine", havingValue = "keyword")
public class KeywordGroundingEngine implements GroundingEngine {

    private static final int MIN_TERM_MATCHES = 2;
    private static final int EXCERPT_CHARS_BEFORE = 40;
    private static final int EXCERPT_CHARS_AFTER = 80;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "to", "in", "on", "for", "of", "is", "it", "i", "my", "how", "do"
    );

    @Override
    public GroundingResult answer(String question, List<GroundingSource> sources) {
        List<String> terms = new ArrayList<>(new LinkedHashSet<>(tokenize(question)));
        if (terms.isEmpty() || sources.isEmpty()) {
            return lowConfidenceResult();
        }

        GroundingSource bestSource = null;
        int bestScore = 0;
        String bestExcerpt = "";

        for (GroundingSource source : sources) {
            String textContent = source.textContent();
            if (textContent == null || textContent.isBlank()) {
                continue;
            }

            int score = scoreTerms(terms, textContent);
            if (score > bestScore) {
                bestScore = score;
                bestSource = source;
                bestExcerpt = excerpt(textContent, terms);
            }
        }

        if (bestSource == null || bestScore < MIN_TERM_MATCHES || bestExcerpt.isBlank()) {
            return lowConfidenceResult();
        }

        String answerBody = "Based on \"" + bestSource.title() + "\": " + bestExcerpt;
        return new GroundingResult(
                answerBody,
                AnswerConfidence.HIGH,
                List.of(new SourceCitation(bestSource.resourceId(), bestSource.title(), bestExcerpt))
        );
    }

    private static GroundingResult lowConfidenceResult() {
        return new GroundingResult(
                "I do not have enough approved material to answer confidently. Please ask a TA or instructor for help.",
                AnswerConfidence.LOW,
                List.of()
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
        if (textContent == null || textContent.isBlank()) {
            return 0;
        }

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
        if (textContent == null || textContent.isBlank()) {
            return "";
        }

        String normalized = textContent.toLowerCase(Locale.ROOT);
        for (String term : terms) {
            int index = normalized.indexOf(term);
            if (index >= 0) {
                int start = Math.max(0, index - EXCERPT_CHARS_BEFORE);
                int end = Math.min(textContent.length(), index + term.length() + EXCERPT_CHARS_AFTER);
                return textContent.substring(start, end).trim();
            }
        }
        return "";
    }
}
