package com.chanter.media.infra;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.media.application.ResourceIngestionClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Profile("!test")
public class HttpResourceIngestionClient implements ResourceIngestionClient {

    private static final Logger log = LoggerFactory.getLogger(HttpResourceIngestionClient.class);

    private final RestClient restClient;
    private final String serviceToken;

    public HttpResourceIngestionClient(
            @Value("${chanter.agent-service.base-url:http://localhost:8085}") String agentServiceBaseUrl,
            @Value("${chanter.agent-service.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${chanter.agent-service.read-timeout-seconds:15}") int readTimeoutSeconds,
            @Value("${chanter.agent-service.service-token:${CHANTER_INTERNAL_SERVICE_TOKEN:}}") String serviceToken
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
        this.serviceToken = serviceToken == null ? "" : serviceToken;
    }

    @Override
    public void ingestAiApprovedResource(UUID courseId, UUID resourceId, String fileName, byte[] content) {
        if (!isTextResource(fileName)) {
            log.info(
                    "Skipping resource ingestion for unsupported file resourceId={} fileName={}",
                    resourceId,
                    fileName
            );
            return;
        }
        if (content == null) {
            content = new byte[0];
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("courseId", courseId);
        body.put("resourceId", resourceId);
        body.put("fileName", fileName);
        body.put("contentBase64", Base64.getEncoder().encodeToString(content));

        try {
            restClient.post()
                    .uri("/api/v1/internal/resource-chunks/ingest")
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, serviceToken)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn(
                    "Failed to ingest AI-approved resource chunks resourceId={} courseId={}: {}",
                    resourceId,
                    courseId,
                    exception.getMessage()
            );
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
            log.warn(
                    "Failed to delete resource chunks resourceId={}: {}",
                    resourceId,
                    exception.getMessage()
            );
        }
    }

    private static boolean isTextResource(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown");
    }
}
