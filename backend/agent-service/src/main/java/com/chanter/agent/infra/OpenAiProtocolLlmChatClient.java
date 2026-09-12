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

/** OpenAI Chat Completions wire contract; provider identity is explicit and never inferred from a URL. */
public class OpenAiProtocolLlmChatClient implements LlmChatClient {
    private final LlmHttpTransport transport;
    private final String key;
    private final String model;
    private final String provider;
    public OpenAiProtocolLlmChatClient(URI endpoint, String key, String model, String provider) {
        this.transport = new LlmHttpTransport(endpoint); this.key = key; this.model = model; this.provider = provider;
    }
    @Override public boolean isEnabled() { return true; }
    @Override public String providerId() { return provider; }
    @Override public String modelId() { return model; }
    @Override public boolean ping() { return false; }
    @Override public LlmChatResponse complete(LlmChatRequest request) {
        try (LlmExecution execution = new LlmExecution(Duration.ofSeconds(30))) { return complete(request, execution); }
    }
    @Override public LlmChatResponse complete(LlmChatRequest request, LlmExecution execution) {
        JsonNode response = transport.post("/chat/completions", Map.of("Authorization", "Bearer " + key),
                Map.of("model", model, "max_completion_tokens", request.maxOutputTokens(), "messages", List.of(
                        Map.of("role", "system", "content", request.systemPrompt()), Map.of("role", "user", "content", request.userMessage()))), execution);
        JsonNode choice = response.path("choices").path(0);
        String finish = LlmHttpTransport.text(choice, "finish_reason");
        if (choice.path("message").path("refusal").isTextual() || "content_filter".equals(finish))
            throw new LlmProviderException(LlmProviderException.Outcome.REFUSED);
        if (!"stop".equals(finish)) throw new LlmProviderException(LlmProviderException.Outcome.LIMIT_EXCEEDED);
        String content = LlmHttpTransport.text(choice.path("message"), "content");
        if (content == null || content.isBlank()) throw new LlmProviderException(LlmProviderException.Outcome.INVALID_RESPONSE);
        JsonNode u = response.path("usage");
        LlmUsage usage = new LlmUsage(LlmHttpTransport.count(u, "prompt_tokens"), LlmHttpTransport.count(u, "completion_tokens"),
                LlmHttpTransport.count(u.path("prompt_tokens_details"), "cached_tokens"), null,
                LlmHttpTransport.count(u.path("completion_tokens_details"), "reasoning_tokens"));
        return new LlmChatResponse(content, response.path("model").asText(model), usage.inputTokens(), usage.outputTokens(), usage,
                LlmHttpTransport.text(response, "id"), finish);
    }

    @Override public LlmChatResponse stream(LlmChatRequest request, LlmExecution execution, java.util.function.Consumer<String> chunks) {
        return transport.exchange("/chat/completions", Map.of("Authorization", "Bearer " + key),
                Map.of("model", model, "max_completion_tokens", request.maxOutputTokens(), "stream", true,
                        "stream_options", Map.of("include_usage", true), "messages", List.of(
                        Map.of("role", "system", "content", request.systemPrompt()), Map.of("role", "user", "content", request.userMessage()))),
                execution, input -> {
                    StringBuilder content = new StringBuilder();
                    String[] metadata = new String[]{null, model, null};
                    LlmUsage[] usage = new LlmUsage[]{LlmUsage.UNKNOWN};
                    boolean[] done = new boolean[]{false};
                    LlmHttpTransport.events(input, false, execution, event -> {
                        if (event == null) { done[0] = true; return; }
                        if (event.has("error")) throw new LlmProviderException(LlmProviderException.Outcome.UNAVAILABLE);
                        if (event.hasNonNull("id")) metadata[0] = event.path("id").asText();
                        if (event.hasNonNull("model")) metadata[1] = event.path("model").asText();
                        JsonNode choice = event.path("choices").path(0);
                        if (choice.path("delta").path("refusal").isTextual()) throw new LlmProviderException(LlmProviderException.Outcome.REFUSED);
                        if (choice.hasNonNull("finish_reason")) metadata[2] = choice.path("finish_reason").asText();
                        String text = LlmHttpTransport.text(choice.path("delta"), "content");
                        if (text != null) { content.append(text); chunks.accept(text); }
                        if (event.hasNonNull("usage")) {
                            JsonNode u = event.path("usage");
                            usage[0] = new LlmUsage(LlmHttpTransport.count(u, "prompt_tokens"), LlmHttpTransport.count(u, "completion_tokens"),
                                    LlmHttpTransport.count(u.path("prompt_tokens_details"), "cached_tokens"), null,
                                    LlmHttpTransport.count(u.path("completion_tokens_details"), "reasoning_tokens"));
                        }
                    });
                    if (!done[0] || content.isEmpty() || metadata[2] == null) throw new LlmProviderException(LlmProviderException.Outcome.INVALID_RESPONSE);
                    if ("content_filter".equals(metadata[2])) throw new LlmProviderException(LlmProviderException.Outcome.REFUSED);
                    if (!"stop".equals(metadata[2])) throw new LlmProviderException(LlmProviderException.Outcome.LIMIT_EXCEEDED);
                    return new LlmChatResponse(content.toString(), metadata[1], usage[0].inputTokens(), usage[0].outputTokens(), usage[0], metadata[0], metadata[2]);
                });
    }
}
