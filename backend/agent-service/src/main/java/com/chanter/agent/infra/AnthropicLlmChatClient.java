package com.chanter.agent.infra;

import com.chanter.agent.application.LlmChatClient;
import com.chanter.agent.application.LlmExecution;
import com.chanter.agent.application.LlmProviderException;
import com.chanter.agent.application.LlmUsage;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class AnthropicLlmChatClient implements LlmChatClient {
    private final LlmHttpTransport transport;
    private final String key;
    private final String model;
    public AnthropicLlmChatClient(URI endpoint, String key, String model) {
        this.transport = new LlmHttpTransport(endpoint); this.key = key; this.model = model;
    }
    @Override public boolean isEnabled() { return true; }
    @Override public String providerId() { return "anthropic"; }
    @Override public String modelId() { return model; }
    @Override public boolean ping() { return false; } // Configuration is not proof of provider readiness.
    @Override public LlmChatResponse complete(LlmChatRequest request) {
        try (LlmExecution execution = new LlmExecution(Duration.ofSeconds(30))) { return complete(request, execution); }
    }
    @Override public LlmChatResponse complete(LlmChatRequest request, LlmExecution execution) {
        JsonNode response = transport.post("/messages", Map.of("x-api-key", key, "anthropic-version", "2023-06-01"),
                Map.of("model", model, "system", request.systemPrompt(), "max_tokens", request.maxOutputTokens(),
                        "messages", List.of(Map.of("role", "user", "content", request.userMessage()))), execution);
        String finish = LlmHttpTransport.text(response, "stop_reason");
        if ("refusal".equals(finish)) throw new LlmProviderException(LlmProviderException.Outcome.REFUSED);
        if (!"end_turn".equals(finish)) throw new LlmProviderException(LlmProviderException.Outcome.LIMIT_EXCEEDED);
        StringBuilder content = new StringBuilder();
        for (JsonNode block : response.path("content")) if ("text".equals(block.path("type").asText())) content.append(block.path("text").asText());
        JsonNode usageNode = response.path("usage");
        Integer input = LlmHttpTransport.count(usageNode, "input_tokens");
        Integer read = LlmHttpTransport.count(usageNode, "cache_read_input_tokens");
        Integer write = LlmHttpTransport.count(usageNode, "cache_creation_input_tokens");
        Integer total = input == null ? null : Math.addExact(input, Math.addExact(read == null ? 0 : read, write == null ? 0 : write));
        LlmUsage usage = new LlmUsage(total, LlmHttpTransport.count(usageNode, "output_tokens"), read, write,
                LlmHttpTransport.count(usageNode.path("output_tokens_details"), "thinking_tokens"));
        if (content.isEmpty()) throw new LlmProviderException(LlmProviderException.Outcome.INVALID_RESPONSE);
        return new LlmChatResponse(content.toString(), response.path("model").asText(model), usage.inputTokens(), usage.outputTokens(),
                usage, LlmHttpTransport.text(response, "id"), finish);
    }

    @Override public LlmChatResponse stream(LlmChatRequest request, LlmExecution execution, java.util.function.Consumer<String> chunks) {
        return transport.exchange("/messages", Map.of("x-api-key", key, "anthropic-version", "2023-06-01"),
                Map.of("model", model, "system", request.systemPrompt(), "max_tokens", request.maxOutputTokens(), "stream", true,
                        "messages", List.of(Map.of("role", "user", "content", request.userMessage()))), execution, input -> {
                    StringBuilder content = new StringBuilder();
                    var mergedUsage = LlmHttpTransport.JSON.createObjectNode();
                    String[] metadata = new String[]{null, model, null};
                    boolean[] done = new boolean[]{false};
                    boolean[] started = new boolean[]{false};
                    LlmHttpTransport.events(input, false, execution, event -> {
                        if (event == null || done[0]) throw new LlmProviderException(LlmProviderException.Outcome.INVALID_RESPONSE);
                        switch (event.path("type").asText()) {
                            case "message_start" -> {
                                if (started[0]) throw new LlmProviderException(LlmProviderException.Outcome.INVALID_RESPONSE);
                                started[0] = true;
                                var message = event.path("message");
                                metadata[0] = LlmHttpTransport.text(message, "id");
                                metadata[1] = message.path("model").asText(model);
                                if (message.path("usage").isObject()) mergedUsage.setAll((com.fasterxml.jackson.databind.node.ObjectNode) message.path("usage"));
                            }
                            case "content_block_delta" -> {
                                if (!started[0]) throw new LlmProviderException(LlmProviderException.Outcome.INVALID_RESPONSE);
                                var delta = event.path("delta");
                                if ("text_delta".equals(delta.path("type").asText())) {
                                    String text = delta.path("text").asText(); content.append(text); chunks.accept(text);
                                }
                            }
                            case "message_delta" -> {
                                if (event.path("delta").hasNonNull("stop_reason")) metadata[2] = event.path("delta").path("stop_reason").asText();
                                if (event.path("usage").isObject()) mergedUsage.setAll((com.fasterxml.jackson.databind.node.ObjectNode) event.path("usage"));
                            }
                            case "message_stop" -> done[0] = true;
                            case "error" -> throw new LlmProviderException(LlmProviderException.Outcome.UNAVAILABLE);
                            default -> { /* Provider may add event types; only text is exposed. */ }
                        }
                    });
                    if ("refusal".equals(metadata[2])) throw new LlmProviderException(LlmProviderException.Outcome.REFUSED);
                    if (!started[0] || !done[0] || content.isEmpty() || metadata[2] == null) throw new LlmProviderException(LlmProviderException.Outcome.INVALID_RESPONSE);
                    if (!"end_turn".equals(metadata[2])) throw new LlmProviderException(LlmProviderException.Outcome.LIMIT_EXCEEDED);
                    Integer uncached = LlmHttpTransport.count(mergedUsage, "input_tokens");
                    Integer read = LlmHttpTransport.count(mergedUsage, "cache_read_input_tokens");
                    Integer write = LlmHttpTransport.count(mergedUsage, "cache_creation_input_tokens");
                    Integer total = uncached == null ? null : Math.addExact(uncached, Math.addExact(read == null ? 0 : read, write == null ? 0 : write));
                    var usage = new LlmUsage(total, LlmHttpTransport.count(mergedUsage, "output_tokens"), read, write,
                            LlmHttpTransport.count(mergedUsage.path("output_tokens_details"), "thinking_tokens"));
                    return new LlmChatResponse(content.toString(), metadata[1], usage.inputTokens(), usage.outputTokens(), usage, metadata[0], metadata[2]);
                });
    }
}
