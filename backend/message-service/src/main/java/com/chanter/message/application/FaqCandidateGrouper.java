package com.chanter.message.application;

import com.chanter.message.domain.SupportQuestion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FaqCandidateGrouper {

    private static final double MIN_JACCARD_SIMILARITY = 0.5;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "to", "in", "on", "for", "of", "is", "it", "i", "my", "how", "do"
    );

    public List<FaqCandidateGroup> group(List<SupportQuestion> supportQuestions) {
        if (supportQuestions.size() < 2) {
            return List.of();
        }

        int size = supportQuestions.size();
        int[] parent = new int[size];
        for (int index = 0; index < size; index++) {
            parent[index] = index;
        }

        List<Set<String>> tokenSets = supportQuestions.stream()
                .map(question -> significantTokens(question.body()))
                .toList();

        for (int left = 0; left < size; left++) {
            for (int right = left + 1; right < size; right++) {
                if (jaccardSimilarity(tokenSets.get(left), tokenSets.get(right)) >= MIN_JACCARD_SIMILARITY) {
                    union(parent, left, right);
                }
            }
        }

        Map<Integer, List<SupportQuestion>> grouped = new HashMap<>();
        for (int index = 0; index < size; index++) {
            grouped.computeIfAbsent(find(parent, index), ignored -> new ArrayList<>())
                    .add(supportQuestions.get(index));
        }

        return grouped.values().stream()
                .filter(group -> group.size() >= 2)
                .map(this::toCandidateGroup)
                .toList();
    }

    private FaqCandidateGroup toCandidateGroup(List<SupportQuestion> group) {
        String representativeQuestion = group.stream()
                .map(SupportQuestion::body)
                .min(String::compareToIgnoreCase)
                .orElse("");
        return new FaqCandidateGroup(representativeQuestion, List.copyOf(group));
    }

    static Set<String> significantTokens(String text) {
        String[] rawTerms = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        Set<String> tokens = new LinkedHashSet<>();
        for (String term : rawTerms) {
            if (term.length() >= 3 && !STOP_WORDS.contains(term)) {
                tokens.add(term);
            }
        }
        return tokens;
    }

    private static double jaccardSimilarity(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 1.0;
        }
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }

        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);

        int intersectionSize = 0;
        for (String token : left) {
            if (right.contains(token)) {
                intersectionSize++;
            }
        }

        return (double) intersectionSize / union.size();
    }

    private static int find(int[] parent, int index) {
        if (parent[index] != index) {
            parent[index] = find(parent, parent[index]);
        }
        return parent[index];
    }

    private static void union(int[] parent, int left, int right) {
        int leftRoot = find(parent, left);
        int rightRoot = find(parent, right);
        if (leftRoot != rightRoot) {
            parent[rightRoot] = leftRoot;
        }
    }
}
