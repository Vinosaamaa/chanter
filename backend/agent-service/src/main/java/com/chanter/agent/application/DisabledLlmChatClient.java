package com.chanter.agent.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Default LLM client when {@code chanter.llm.enabled=false} (local product without a model).
 */
@Component
@ConditionalOnProperty(name = "chanter.llm.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledLlmChatClient implements LlmChatClient {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String providerId() {
        return "disabled";
    }

    @Override
    public String modelId() {
        return "none";
    }

    @Override
    public LlmChatResponse complete(LlmChatRequest request) {
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "LLM provider is disabled. Set CHANTER_LLM_ENABLED=true and configure Ollama or an OpenAI-compatible endpoint."
        );
    }

    @Override
    public boolean ping() {
        return false;
    }
}
