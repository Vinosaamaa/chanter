package com.chanter.agent.application;

public interface LlmChatClient {

    boolean isEnabled();

    String providerId();

    String modelId();

    LlmChatResponse complete(LlmChatRequest request);

    /**
     * Lightweight reachability probe. Returns true when the provider answers.
     */
    boolean ping();

    record LlmChatRequest(String systemPrompt, String userMessage) {
    }

    record LlmChatResponse(String content, String model, Integer promptTokens, Integer completionTokens) {
    }
}
