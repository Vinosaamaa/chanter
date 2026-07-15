package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HashingEmbeddingClientTest {

    private final HashingEmbeddingClient client = new HashingEmbeddingClient(64);

    @Test
    void similarTextsRankHigherThanUnrelated() {
        float[] homework = client.embed("Submit homework before the deadline using the portal.");
        float[] similar = client.embed("How do I submit homework before the deadline?");
        float[] weather = client.embed("What is the weather in Tokyo today?");

        double similarScore = EmbeddingCodec.cosineSimilarity(homework, similar);
        double unrelatedScore = EmbeddingCodec.cosineSimilarity(homework, weather);

        assertThat(similarScore).isGreaterThan(unrelatedScore);
        assertThat(client.modelId()).startsWith("hashing-v1-");
        assertThat(client.dimensions()).isEqualTo(64);
    }

    @Test
    void emptyTextReturnsZeroVector() {
        float[] empty = client.embed(" ");
        double sum = 0.0;
        for (float value : empty) {
            sum += Math.abs(value);
        }
        assertThat(sum).isZero();
    }
}
