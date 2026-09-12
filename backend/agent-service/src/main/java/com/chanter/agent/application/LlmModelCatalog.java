package com.chanter.agent.application;

import com.chanter.agent.config.LlmProperties;
import com.chanter.agent.config.LlmProperties.Model;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LlmModelCatalog {
    public static final String SOURCE_ONLY = "source-only";
    private static final Map<String, String> ENDPOINTS = Map.of(
            "openai", "https://api.openai.com/v1", "anthropic", "https://api.anthropic.com/v1",
            "xai", "https://api.x.ai/v1", "ollama", "http://localhost:11434");
    private final LlmProperties properties;
    private final Map<String, LlmChatClient> clients = new java.util.concurrent.ConcurrentHashMap<>();

    public LlmModelCatalog(LlmProperties properties) {
        this.properties = properties;
        if (properties.enabled() && properties.models().isEmpty()) {
            throw new IllegalArgumentException("Configure chanter.llm.models explicitly before enabling generation");
        }
        if (properties.dailyTokenLimit() < 1 || properties.dailyTokenLimit() > 100_000_000) {
            throw new IllegalArgumentException("Invalid AI daily token limit");
        }
        properties.models().forEach(this::validate);
        if (!SOURCE_ONLY.equals(properties.defaultModelId()) && !properties.models().containsKey(properties.defaultModelId())) {
            throw new IllegalArgumentException("AI default model must be a configured catalog ID");
        }
    }

    public String defaultModelId() { return properties.enabled() ? properties.defaultModelId() : SOURCE_ONLY; }
    public long dailyTokenLimit() { return properties.dailyTokenLimit(); }
    public Map<String, Model> configuredModels() { return properties.enabled() ? properties.models() : Map.of(); }
    public Model definition(String id) {
        if (!properties.enabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI generation is disabled");
        Model model = properties.models().get(id);
        if (model == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unknown AI model");
        return model;
    }
    public LlmChatClient client(String id) {
        if (!properties.enabled()) return clients.computeIfAbsent(SOURCE_ONLY, ignored -> new DisabledLlmChatClient());
        return clients.computeIfAbsent(id, selection -> {
            if (SOURCE_ONLY.equals(selection)) return new DisabledLlmChatClient();
            Model config = definition(selection);
            URI endpoint = URI.create(baseUrl(config));
            return switch (config.provider()) {
                case "anthropic" -> new com.chanter.agent.infra.AnthropicLlmChatClient(endpoint, config.apiKey(), config.model());
                case "xai" -> new com.chanter.agent.infra.XaiLlmChatClient(endpoint, config.apiKey(), config.model());
                case "ollama" -> new com.chanter.agent.infra.OllamaLlmChatClient(baseUrl(config), config.model(), 5, (int) config.timeout().toSeconds());
                case "compatible" -> new com.chanter.agent.infra.OpenAiProtocolLlmChatClient(endpoint, config.apiKey(), config.model(), "compatible");
                default -> new com.chanter.agent.infra.ResponsesLlmChatClient(endpoint, config.apiKey(), config.model(), config.provider());
            };
        });
    }
    public String baseUrl(Model model) { return model.baseUrl() == null || model.baseUrl().isBlank()
            ? ENDPOINTS.get(model.provider()) : model.baseUrl(); }

    public List<ModelView> modelsFor(UUID courseId) {
        List<ModelView> result = new ArrayList<>();
        result.add(new ModelView(SOURCE_ONLY, "Approved sources", "none", "none", "sources", "no-provider-charge"));
        properties.models().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Model model = entry.getValue();
            if (permitted(model, courseId)) result.add(new ModelView(entry.getKey(),
                    model.label() == null || model.label().isBlank() ? entry.getKey() : model.label(),
                    model.provider(), model.model(), "ollama".equals(model.provider()) ? "local" : "api",
                    switch (model.provider()) {
                        case "ollama" -> "local-compute";
                        case "xai" -> "provider-account-dependent";
                        default -> "separate-api-billing";
                    }));
        });
        return List.copyOf(result);
    }

    public String select(String requested, UUID courseId) {
        String id = requested == null || requested.isBlank() ? defaultModelId() : requested;
        if (SOURCE_ONLY.equals(id)) return id;
        Model model = properties.models().get(id);
        if (model == null || !permitted(model, courseId)) {
            if (requested == null || requested.isBlank()) return SOURCE_ONLY;
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI model is not available for this Course");
        }
        return id;
    }

    private boolean permitted(Model model, UUID courseId) {
        return properties.enabled() && ("ollama".equals(model.provider()) || model.allowedCourseIds().contains(courseId));
    }

    private void validate(String id, Model model) {
        // Configuration errors deliberately omit values: endpoints and credentials are private.
        if (!id.matches("[a-z][a-z0-9-]{0,47}") || SOURCE_ONLY.equals(id) || model == null
                || model.provider() == null || !Set.of("openai", "anthropic", "xai", "ollama", "compatible").contains(model.provider())
                || model.model() == null || !model.model().matches("[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,127}")) fail();
        String provider = model.provider();
        String name = model.model().toLowerCase(Locale.ROOT);
        if ("anthropic".equals(provider) && !name.startsWith("claude-")) fail();
        if ("xai".equals(provider) && !name.startsWith("grok-")) fail();
        if ("openai".equals(provider) && !(name.startsWith("gpt-") || name.matches("o[0-9].*"))) fail();
        if ("ollama".equals(provider) && (name.contains("cloud") || name.startsWith("gpt-") || name.startsWith("claude-"))) fail();
        if (!"ollama".equals(provider) && (model.apiKey() == null || model.apiKey().isBlank())) fail();
        URI uri;
        try { uri = URI.create(baseUrl(model)); } catch (RuntimeException e) { throw invalid(); }
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) fail();
        if ("ollama".equals(provider)) {
            if (!"http".equals(uri.getScheme()) || !Set.of("localhost", "127.0.0.1", "[::1]", "ollama").contains(uri.getHost())) fail();
        } else {
            if (!"https".equals(uri.getScheme())) fail();
            if (!"compatible".equals(provider) && !baseUrl(model).equals(ENDPOINTS.get(provider))) fail();
        }
        if (model.maxInputTokens() < 64 || model.maxInputTokens() > 65536 || model.maxOutputTokens() < 1
                || model.maxOutputTokens() > 8192 || model.timeout() == null || model.timeout().isNegative()
                || model.timeout().compareTo(java.time.Duration.ofSeconds(1)) < 0
                || model.timeout().compareTo(java.time.Duration.ofSeconds(120)) > 0) fail();
        if (model.price() != null) {
            var p = model.price();
            if (p.version() == null || !p.version().matches("[a-zA-Z0-9._-]{1,64}")) fail();
            if (p.input() == null || p.output() == null || p.cacheRead() == null || p.cacheWrite() == null) fail();
            if (List.of(p.input(), p.output(), p.cacheRead(), p.cacheWrite()).stream().anyMatch(v -> v.signum() < 0)) fail();
        }
    }
    private static void fail() { throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid AI model catalog configuration"); }
    public record ModelView(String id, String label, String provider, String model, String mode, String billing) {}
}
