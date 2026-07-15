package com.chanter.agent.application;

public interface EmbeddingClient {

    String modelId();

    int dimensions();

    float[] embed(String text);
}
