package com.chanter.analytics.infra;

import com.chanter.analytics.config.AgentServiceClientProperties;
import com.chanter.common.auth.AuthHeaders;
import java.net.http.HttpClient;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpAgentServiceClient {

    private final RestClient restClient;
    private final String internalServiceToken;

    public HttpAgentServiceClient(
            AgentServiceClientProperties properties,
            @Value("${chanter.internal-service-token}") String internalServiceToken
    ) {
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
        this.internalServiceToken = internalServiceToken;
    }

    public AiUsageMetricsResponse fetchAiUsageMetrics(UUID studyServerId, UUID viewerUserId) {
        try {
            AiUsageMetricsResponse response = restClient.get()
                    .uri("/api/v1/study-servers/{studyServerId}/ai-usage-metrics", studyServerId)
                    .header(AuthHeaders.USER_ID, viewerUserId.toString())
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, internalServiceToken)
                    .retrieve()
                    .body(AiUsageMetricsResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Agent Service returned empty AI usage metrics");
            }

            return response;
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Instructor Dashboard AI usage metrics denied", exception);
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Agent Service AI usage metrics request failed", exception);
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Agent Service is unavailable", exception);
        } catch (ResourceAccessException exception) {
            throw DownstreamRestClientErrors.mapResourceAccess(
                    exception,
                    "Agent Service AI usage metrics request timed out",
                    "Unable to reach Agent Service"
            );
        } catch (RestClientException exception) {
            throw DownstreamRestClientErrors.mapRestClient(exception, "Unable to reach Agent Service");
        }
    }

    public record AiUsageMetricsResponse(
            UUID studyServerId,
            String planTier,
            int totalInvocations,
            int aiInvocationLimit,
            int remainingInvocations,
            boolean quotaExhausted,
            int lowConfidenceHandoffs
    ) {
    }
}
