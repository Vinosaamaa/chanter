package com.chanter.agent.infra;

import com.chanter.agent.application.EmbeddingClient;
import com.chanter.agent.application.EmbeddingCodec;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Optional Ollama embeddings provider for local real-model development.
 * Enable with {@code chanter.embeddings.provider=ollama}.
 */
@Component
@ConditionalOnProperty(name = "chanter.embeddings.provider", havingValue = "ollama")
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final String model;
    private final int dimensions;

    public OllamaEmbeddingClient(
            @Value("${chanter.embeddings.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${chanter.embeddings.ollama.model:nomic-embed-text}") String model,
            @Value("${chanter.embeddings.dimensions:768}") int dimensions,
            @Value("${chanter.embeddings.ollama.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${chanter.embeddings.ollama.read-timeout-seconds:60}") int readTimeoutSeconds
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                        .build()
        );
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.model = model;
        this.dimensions = dimensions;
    }

    @Override
    public String modelId() {
        return "ollama:" + model;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public float[] embed(String text) {
        try {
            OllamaEmbeddingResponse response = restClient.post()
                    .uri("/api/embeddings")
                    .body(Map.of("model", model, "prompt", text == null ? "" : text))
                    .retrieve()
                    .body(OllamaEmbeddingResponse.class);
            if (response == null || response.embedding() == null || response.embedding().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ollama returned an empty embedding");
            }
            float[] vector = new float[response.embedding().size()];
            for (int i = 0; i < response.embedding().size(); i++) {
                vector[i] = response.embedding().get(i).floatValue();
            }
            return EmbeddingCodec.l2Normalize(vector);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to reach Ollama embeddings endpoint",
                    exception
            );
        }
    }

    private record OllamaEmbeddingResponse(List<Double> embedding) {
    }
}
