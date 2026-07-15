package com.chanter.agent.infra;

import com.chanter.agent.application.LlmChatClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnExpression("'${chanter.llm.enabled:false}' == 'true' && '${chanter.llm.provider:ollama}' == 'openai'")
public class OpenAiCompatibleLlmChatClient implements LlmChatClient {

    private final RestClient restClient;
    private final String model;
    private final String apiKey;

    @Autowired
    public OpenAiCompatibleLlmChatClient(
            @Value("${chanter.llm.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${chanter.llm.openai.api-key:}") String apiKey,
            @Value("${chanter.llm.model:gpt-4o-mini}") String model,
            @Value("${chanter.llm.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${chanter.llm.read-timeout-seconds:120}") int readTimeoutSeconds
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeoutSeconds)).build()
        );
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
    }

    OpenAiCompatibleLlmChatClient(RestClient restClient, String apiKey, String model) {
        this.restClient = restClient;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String providerId() {
        return "openai";
    }

    @Override
    public String modelId() {
        return model;
    }

    @Override
    public LlmChatResponse complete(LlmChatRequest request) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OPENAI_API_KEY is not configured for the OpenAI-compatible provider."
            );
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", nullToEmpty(request.systemPrompt())),
                Map.of("role", "user", "content", nullToEmpty(request.userMessage()))
        ));
        try {
            OpenAiChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(OpenAiChatResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()
                    || response.choices().getFirst().message() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI-compatible provider returned an empty response");
            }
            Integer prompt = response.usage() == null ? null : response.usage().prompt_tokens();
            Integer completion = response.usage() == null ? null : response.usage().completion_tokens();
            return new LlmChatResponse(
                    response.choices().getFirst().message().content().trim(),
                    model,
                    prompt,
                    completion
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The AI provider is temporarily unavailable. Please try again later.",
                    exception
            );
        }
    }

    @Override
    public boolean ping() {
        if (apiKey.isBlank()) {
            return false;
        }
        try {
            restClient.get()
                    .uri("/models")
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record OpenAiChatResponse(List<Choice> choices, Usage usage) {
        private record Choice(Message message) {
        }

        private record Message(String content) {
        }

        private record Usage(Integer prompt_tokens, Integer completion_tokens) {
        }
    }
}
