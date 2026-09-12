package com.chanter.agent.application;

public interface LlmChatClient {

    boolean isEnabled();

    String providerId();

    String modelId();

    LlmChatResponse complete(LlmChatRequest request);

    default LlmChatResponse complete(LlmChatRequest request, LlmExecution execution) {
        execution.check();
        LlmChatResponse response = complete(request);
        execution.check();
        return response;
    }

    default LlmChatResponse stream(LlmChatRequest request, LlmExecution execution, java.util.function.Consumer<String> chunks) {
        LlmChatResponse response = complete(request, execution);
        chunks.accept(response.content());
        return response;
    }

    /**
     * Lightweight reachability probe. Returns true when the provider answers.
     */
    boolean ping();

    record LlmChatRequest(String systemPrompt, String userMessage, int maxOutputTokens) {
        public LlmChatRequest(String systemPrompt, String userMessage) { this(systemPrompt, userMessage, 1024); }
    }

    record LlmChatResponse(String content, String model, Integer promptTokens, Integer completionTokens,
                           LlmUsage usage, String requestId, String finishReason) {
        public LlmChatResponse(String content, String model, Integer promptTokens, Integer completionTokens) {
            this(content, model, promptTokens, completionTokens,
                    new LlmUsage(promptTokens, completionTokens, null, null, null), null, null);
        }
    }
}
