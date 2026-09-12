package com.chanter.agent.infra;

import com.chanter.agent.application.LlmChatClient;
import com.chanter.agent.application.LlmExecution;
import com.chanter.agent.application.LlmProviderException;
import com.chanter.agent.application.LlmProviderException.Outcome;
import com.chanter.agent.application.LlmUsage;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Stateless Responses API: bounded output includes reasoning; provider tools and response storage are disabled. */
public class ResponsesLlmChatClient implements LlmChatClient {
    private final LlmHttpTransport transport;
    private final String key;
    private final String model;
    private final String provider;
    public ResponsesLlmChatClient(URI endpoint, String key, String model, String provider) {
        this.transport = new LlmHttpTransport(endpoint); this.key = key; this.model = model; this.provider = provider;
    }
    @Override public boolean isEnabled() { return true; }
    @Override public String providerId() { return provider; }
    @Override public String modelId() { return model; }
    @Override public boolean ping() { return false; }
    @Override public LlmChatResponse complete(LlmChatRequest request) {
        try (LlmExecution execution = new LlmExecution(Duration.ofSeconds(30))) { return complete(request, execution); }
    }
    private Map<String, Object> payload(LlmChatRequest request, boolean stream) {
        return Map.of("model", model, "instructions", request.systemPrompt(), "input", request.userMessage(),
                "max_output_tokens", request.maxOutputTokens(), "stream", stream, "store", false, "tools", List.of(), "tool_choice", "none");
    }
    @Override public LlmChatResponse complete(LlmChatRequest request, LlmExecution execution) {
        var response = transport.post("/responses", Map.of("Authorization", "Bearer " + key), payload(request, false), execution);
        StringBuilder content = new StringBuilder();
        for (JsonNode output : response.path("output")) for (JsonNode block : output.path("content")) {
            if ("refusal".equals(block.path("type").asText())) throw new LlmProviderException(Outcome.REFUSED);
            if ("output_text".equals(block.path("type").asText())) content.append(block.path("text").asText());
        }
        return result(response, content.toString());
    }
    @Override public LlmChatResponse stream(LlmChatRequest request, LlmExecution execution, Consumer<String> chunks) {
        return transport.exchange("/responses", Map.of("Authorization", "Bearer " + key), payload(request, true), execution, input -> {
            StringBuilder content = new StringBuilder();
            JsonNode[] completed = new JsonNode[1];
            LlmHttpTransport.events(input, false, execution, event -> {
                if (event == null) return;
                switch (event.path("type").asText()) {
                    case "response.output_text.delta" -> {
                        if (completed[0] != null) throw new LlmProviderException(Outcome.INVALID_RESPONSE);
                        String text = event.path("delta").asText(); content.append(text); chunks.accept(text);
                    }
                    case "response.completed" -> {
                        if (completed[0] != null) throw new LlmProviderException(Outcome.INVALID_RESPONSE);
                        completed[0] = event.path("response");
                    }
                    case "response.refusal.delta", "response.refusal.done" -> throw new LlmProviderException(Outcome.REFUSED);
                    case "response.incomplete" -> throw new LlmProviderException(Outcome.LIMIT_EXCEEDED);
                    case "error", "response.failed" -> throw new LlmProviderException(Outcome.UNAVAILABLE);
                    default -> { /* Ignore metadata and reasoning; only visible answer text reaches the caller. */ }
                }
            });
            if (completed[0] == null) throw new LlmProviderException(Outcome.INVALID_RESPONSE);
            return result(completed[0], content.toString());
        });
    }
    private LlmChatResponse result(JsonNode response, String content) {
        if (!"completed".equals(response.path("status").asText()) || content.isBlank())
            throw new LlmProviderException(Outcome.INVALID_RESPONSE);
        var u = response.path("usage");
        LlmUsage usage = new LlmUsage(LlmHttpTransport.count(u, "input_tokens"), LlmHttpTransport.count(u, "output_tokens"),
                LlmHttpTransport.count(u.path("input_tokens_details"), "cached_tokens"), null,
                LlmHttpTransport.count(u.path("output_tokens_details"), "reasoning_tokens"));
        return new LlmChatResponse(content, response.path("model").asText(model), usage.inputTokens(), usage.outputTokens(), usage,
                LlmHttpTransport.text(response, "id"), "completed");
    }
}
