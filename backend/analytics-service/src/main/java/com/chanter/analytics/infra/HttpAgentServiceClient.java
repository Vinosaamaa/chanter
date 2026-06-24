package com.chanter.analytics.infra;

import com.chanter.analytics.config.AgentServiceClientProperties;
import java.net.http.HttpClient;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpAgentServiceClient {

    private final RestClient restClient;

    public HttpAgentServiceClient(AgentServiceClientProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(properties.connectTimeout())
                        .build()
        );
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public AiUsageMetricsResponse fetchAiUsageMetrics(UUID studyServerId, UUID viewerUserId) {
        try {
            AiUsageMetricsResponse response = restClient.get()
                    .uri("/api/v1/study-servers/{studyServerId}/ai-usage-metrics?viewerUserId={viewerUserId}",
                            studyServerId, viewerUserId)
                    .retrieve()
                    .body(AiUsageMetricsResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Agent Service returned empty AI usage metrics");
            }

            return response;
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Instructor Dashboard AI usage metrics denied", exception);
        }
    }

    public record AiUsageMetricsResponse(
            UUID studyServerId,
            int totalInvocations,
            int lowConfidenceHandoffs
    ) {
    }
}
