package com.chanter.agent.application;

public interface EmbeddingClient {

    String modelId();

    int dimensions();

    float[] embed(String text);

    default com.chanter.agent.domain.EmbeddingModel metadata() {
        return new com.chanter.agent.domain.EmbeddingModel(modelId(), "test", modelId(), "test-v1", dimensions());
    }
}
