package com.chanter.agent.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Client for the free source-only selection.
 */
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
                "No generation model is configured. Approved source answers remain available."
        );
    }

    @Override
    public boolean ping() {
        return false;
    }
}
