package com.chanter.agent.infra;

import com.chanter.agent.application.LlmChatClient;
import com.chanter.agent.application.LlmExecution;
import com.chanter.agent.application.LlmProviderException;
import com.chanter.agent.application.LlmUsage;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class OllamaLlmChatClient implements LlmChatClient {
    private final String model;
    private final LlmHttpTransport transport;
    private final Duration timeout;

    public OllamaLlmChatClient(String baseUrl, String model, int connectTimeoutSeconds, int readTimeoutSeconds) {
        this.model = model;
        this.transport = new LlmHttpTransport(URI.create(baseUrl));
        this.timeout = Duration.ofSeconds(readTimeoutSeconds);
    }
    @Override public boolean isEnabled() { return true; }
    @Override public String providerId() { return "ollama"; }
    @Override public String modelId() { return model; }
    @Override public boolean ping() { return false; }
    @Override public LlmChatResponse complete(LlmChatRequest request) {
        try (var execution = new LlmExecution(timeout)) { return complete(request, execution); }
    }
    @Override public LlmChatResponse complete(LlmChatRequest request, LlmExecution execution) {
        var response = transport.post("/api/chat", Map.of(), Map.of("model", model, "stream", false,
                "options", Map.of("num_predict", request.maxOutputTokens()), "messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()), Map.of("role", "user", "content", request.userMessage()))), execution);
        String content = LlmHttpTransport.text(response.path("message"), "content");
        if (!response.path("done").asBoolean() || content == null || content.isBlank())
            throw new LlmProviderException(LlmProviderException.Outcome.INVALID_RESPONSE);
        if (!"stop".equals(LlmHttpTransport.text(response, "done_reason")))
            throw new LlmProviderException(LlmProviderException.Outcome.LIMIT_EXCEEDED);
        var usage = new LlmUsage(LlmHttpTransport.count(response, "prompt_eval_count"), LlmHttpTransport.count(response, "eval_count"), null, null, null);
        return new LlmChatResponse(content, response.path("model").asText(model), usage.inputTokens(), usage.outputTokens(), usage, null, "stop");
    }
    @Override public LlmChatResponse stream(LlmChatRequest request, LlmExecution execution, java.util.function.Consumer<String> chunks) {
        return transport.exchange("/api/chat", Map.of(), Map.of("model", model, "stream", true,
                "options", Map.of("num_predict", request.maxOutputTokens()), "messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()), Map.of("role", "user", "content", request.userMessage()))),
                execution, input -> {
                    StringBuilder content = new StringBuilder();
                    LlmUsage[] usage = new LlmUsage[]{LlmUsage.UNKNOWN};
                    String[] metadata = new String[]{model, null};
                    boolean[] done = new boolean[]{false};
                    LlmHttpTransport.events(input, true, execution, event -> {
                        if (event == null || done[0]) throw new LlmProviderException(LlmProviderException.Outcome.INVALID_RESPONSE);
                        if (event.has("error")) throw new LlmProviderException(LlmProviderException.Outcome.UNAVAILABLE);
                        if (event.hasNonNull("model")) metadata[0] = event.path("model").asText();
                        String text = LlmHttpTransport.text(event.path("message"), "content");
                        if (text != null) { content.append(text); chunks.accept(text); }
                        if (event.path("done").asBoolean()) {
                            done[0] = true; metadata[1] = LlmHttpTransport.text(event, "done_reason");
                            usage[0] = new LlmUsage(LlmHttpTransport.count(event, "prompt_eval_count"),
                                    LlmHttpTransport.count(event, "eval_count"), null, null, null);
                        }
                    });
                    if (!done[0] || content.isEmpty()) throw new LlmProviderException(LlmProviderException.Outcome.INVALID_RESPONSE);
                    if (!"stop".equals(metadata[1])) throw new LlmProviderException(LlmProviderException.Outcome.LIMIT_EXCEEDED);
                    return new LlmChatResponse(content.toString(), metadata[0], usage[0].inputTokens(), usage[0].outputTokens(), usage[0], null, metadata[1]);
                });
    }

}
