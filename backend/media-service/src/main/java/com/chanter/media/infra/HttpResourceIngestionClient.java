package com.chanter.media.infra;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.media.application.ResourceIngestionClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Profile("!test")
public class HttpResourceIngestionClient implements ResourceIngestionClient {

    private final RestClient restClient;
    private final String serviceToken;

    public HttpResourceIngestionClient(
            @Value("${chanter.agent-service.base-url:http://localhost:8085}") String agentServiceBaseUrl,
            @Value("${chanter.agent-service.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${chanter.agent-service.read-timeout-seconds:15}") int readTimeoutSeconds,
            @Value("${chanter.agent-service.service-token:${chanter.internal-service-token}}") String serviceToken
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                        .build()
        );
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restClient = RestClient.builder()
                .baseUrl(agentServiceBaseUrl)
                .requestFactory(requestFactory)
                .build();
        this.serviceToken = InternalServiceTokens.require(serviceToken);
    }

    @Override
    public Outcome ingestAiApprovedResource(UUID courseId, UUID resourceId, String fileName, byte[] content) {
        if (content == null) {
            content = new byte[0];
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("courseId", courseId);
        body.put("resourceId", resourceId);
        body.put("fileName", fileName);
        body.put("contentBase64", Base64.getEncoder().encodeToString(content));

        try {
            var result = restClient.post()
                    .uri("/api/v1/internal/resource-chunks/ingest")
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, serviceToken)
                    .body(body)
                    .retrieve()
                    .body(IngestResponse.class);
            if (result == null || !resourceId.equals(result.resourceId()) || !courseId.equals(result.courseId())) {
                throw new IllegalStateException("Invalid resource indexing response");
            }
            return new Outcome(result.status(), result.signals());
        } catch (RestClientException exception) {
            throw new IllegalStateException("Resource indexing is unavailable");
        }
    }

    @Override
    public void deleteResourceChunks(UUID resourceId) {
        try {
            restClient.delete()
                    .uri("/api/v1/internal/resource-chunks/{resourceId}", resourceId)
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, serviceToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IllegalStateException("Resource index deletion is unavailable");
        }
    }

    private record IngestResponse(UUID resourceId, UUID courseId, String status, java.util.Set<String> signals) {}

    @Override
    public void purgeResourceChunks(UUID resourceId) {
        try {
            restClient.post()
                    .uri("/api/v1/internal/resource-chunks/{resourceId}/purge", resourceId)
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, serviceToken)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IllegalStateException("Resource index purge is unavailable");
        }
    }
}
