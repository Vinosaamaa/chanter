package com.chanter.agent.application;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic local embedder for CI and default local product stacks.
 * Uses a hashing / bag-of-words projection into a fixed unit vector — not a neural model.
 * Swap to {@code chanter.embeddings.provider=ollama} for real local embeddings.
 */
@Component
@ConditionalOnProperty(name = "chanter.embeddings.provider", havingValue = "hashing", matchIfMissing = true)
public class HashingEmbeddingClient implements EmbeddingClient {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}_]+");

    private final int dimensions;
    private final String modelId;

    public HashingEmbeddingClient(
            @Value("${chanter.embeddings.dimensions:384}") int dimensions
    ) {
        if (dimensions < 8) {
            throw new IllegalArgumentException("dimensions must be >= 8");
        }
        this.dimensions = dimensions;
        this.modelId = "hashing-v1-" + dimensions;
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimensions];
        if (text == null || text.isBlank()) {
            return vector;
        }

        var matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        int tokenCount = 0;
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 2) {
                continue;
            }
            tokenCount++;
            int bucket = Math.floorMod(fnv1a(token.getBytes(StandardCharsets.UTF_8)), dimensions);
            int sign = (fnv1a(("|" + token).getBytes(StandardCharsets.UTF_8)) & 1) == 0 ? 1 : -1;
            vector[bucket] += sign;
        }

        if (tokenCount == 0) {
            return vector;
        }
        return EmbeddingCodec.l2Normalize(vector);
    }

    private static int fnv1a(byte[] data) {
        int hash = 0x811c9dc5;
        for (byte b : data) {
            hash ^= (b & 0xff);
            hash *= 0x01000193;
        }
        return hash;
    }
}
