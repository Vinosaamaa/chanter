package com.chanter.agent.infra;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.chanter.agent.application.EmbeddingClient;
import com.chanter.agent.application.EmbeddingCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Pinned CPU sentence encoder. Assets are supplied at build time; inference never downloads. */
public final class OnnxEmbeddingClient implements EmbeddingClient, AutoCloseable {
    public static final String REVISION = "1110a243fdf4706b3f48f1d95db1a4f5529b4d41";
    public static final String MODEL_SHA = "6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452";
    public static final String TOKENIZER_SHA = "be50c3628f2bf5bb5e3a7f17b1f74611b2561a3a27eeab05e5aa30f411572037";
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final Semaphore inference = new Semaphore(1, true);
    private volatile boolean closed;

    public OnnxEmbeddingClient(Path directory) {
        verify(directory.resolve("model.onnx"), MODEL_SHA);
        verify(directory.resolve("tokenizer.json"), TOKENIZER_SHA);
        HuggingFaceTokenizer openedTokenizer = null;
        try (var options = new OrtSession.SessionOptions()) {
            openedTokenizer = HuggingFaceTokenizer.builder().optTokenizerPath(directory.resolve("tokenizer.json"))
                    .optTruncation(true).optMaxLength(256).optPadding(false).build();
            options.setIntraOpNumThreads(1);
            options.setInterOpNumThreads(1);
            options.setMemoryPatternOptimization(false);
            environment = OrtEnvironment.getEnvironment();
            session = environment.createSession(directory.resolve("model.onnx").toString(), options);
            tokenizer = openedTokenizer;
        } catch (IOException | OrtException | RuntimeException failed) {
            if (openedTokenizer != null) openedTokenizer.close();
            throw new IllegalStateException("Pinned semantic embedding model could not be initialized", failed);
        }
    }

    @Override public String modelId() { return "minilm-l6-v2:" + REVISION + ":mean-l2-256-v1"; }
    @Override public int dimensions() { return 384; }
    @Override public com.chanter.agent.domain.EmbeddingModel metadata() {
        return new com.chanter.agent.domain.EmbeddingModel(modelId(), "onnx", "sentence-transformers/all-MiniLM-L6-v2",
                REVISION + ":mean-l2-256-v1", dimensions());
    }

    @Override public float[] embed(String text) {
        if (text == null || text.isBlank() || text.length() > 16384) {
            throw new IllegalArgumentException("Embedding input must contain 1 to 16384 characters");
        }
        boolean acquired = false;
        try {
            acquired = inference.tryAcquire(2, TimeUnit.SECONDS);
            if (!acquired || closed) throw new IllegalStateException("Semantic embedding capacity is unavailable");
            var encoded = tokenizer.encode(text);
            try (var ids = OnnxTensor.createTensor(environment, new long[][]{encoded.getIds()});
                 var mask = OnnxTensor.createTensor(environment, new long[][]{encoded.getAttentionMask()});
                 var types = OnnxTensor.createTensor(environment, new long[][]{encoded.getTypeIds()});
                 var result = session.run(Map.of("input_ids", ids, "attention_mask", mask, "token_type_ids", types))) {
                var tokens = ((float[][][]) result.get(0).getValue())[0];
                float[] vector = new float[dimensions()];
                for (int token = 0; token < tokens.length; token++) {
                    if (encoded.getAttentionMask()[token] == 0) continue;
                    for (int dimension = 0; dimension < vector.length; dimension++) vector[dimension] += tokens[token][dimension];
                }
                return EmbeddingCodec.l2Normalize(vector);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Semantic embedding interrupted");
        } catch (OrtException failed) {
            throw new IllegalStateException("Semantic embedding failed", failed);
        } finally { if (acquired) inference.release(); }
    }

    @Override public void close() throws OrtException {
        inference.acquireUninterruptibly();
        try {
            if (!closed) { closed = true; tokenizer.close(); session.close(); }
        } finally { inference.release(); }
    }

    private static void verify(Path file, String expected) {
        try (var input = Files.newInputStream(file)) {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int length;
            while ((length = input.read(buffer)) >= 0) digest.update(buffer, 0, length);
            if (!HexFormat.of().formatHex(digest.digest()).equals(expected)) {
                throw new IllegalStateException("Semantic model asset checksum mismatch");
            }
        } catch (IOException | NoSuchAlgorithmException missing) {
            throw new IllegalStateException("Pinned semantic model assets are unavailable", missing);
        }
    }
}
