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
@ConditionalOnExpression("'${chanter.llm.enabled:false}' == 'true' && '${chanter.llm.provider:ollama}' == 'ollama'")
public class OllamaLlmChatClient implements LlmChatClient {

    private final RestClient restClient;
    private final String model;

    @Autowired
    public OllamaLlmChatClient(
            @Value("${chanter.llm.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${chanter.llm.model:llama3.2}") String model,
            @Value("${chanter.llm.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${chanter.llm.read-timeout-seconds:120}") int readTimeoutSeconds
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeoutSeconds)).build()
        );
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.model = model;
    }

    OllamaLlmChatClient(RestClient restClient, String model) {
        this.restClient = restClient;
        this.model = model;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String providerId() {
        return "ollama";
    }

    @Override
    public String modelId() {
        return model;
    }

    @Override
    public LlmChatResponse complete(LlmChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", nullToEmpty(request.systemPrompt())),
                Map.of("role", "user", "content", nullToEmpty(request.userMessage()))
        ));
        try {
            OllamaChatResponse response = restClient.post()
                    .uri("/api/chat")
                    .body(body)
                    .retrieve()
                    .body(OllamaChatResponse.class);
            if (response == null || response.message() == null || response.message().content() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ollama returned an empty chat response");
            }
            return new LlmChatResponse(response.message().content().trim(), model, null, null);
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
        try {
            restClient.get().uri("/api/tags").retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record OllamaChatResponse(Message message) {
        private record Message(String content) {
        }
    }
}
