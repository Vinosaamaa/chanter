package com.chanter.analytics.infra;

import com.chanter.analytics.config.MessageServiceClientProperties;
import com.chanter.common.auth.AuthHeaders;
import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;
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
public class HttpMessageServiceClient {

    private final RestClient restClient;

    public HttpMessageServiceClient(MessageServiceClientProperties properties) {
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

    public MessageMetricsResponse fetchMessageMetrics(MessageMetricsRequest request) {
        try {
            MessageMetricsResponse response = restClient.post()
                    .uri("/api/v1/instructor-dashboard/message-metrics")
                    .header(AuthHeaders.USER_ID, request.viewerUserId().toString())
                    .body(new MessageMetricsBody(
                            request.questionChannelIds(),
                            request.cohortIds(),
                            request.courseIds()
                    ))
                    .retrieve()
                    .body(MessageMetricsResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Message Service returned empty dashboard metrics");
            }

            return response;
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Instructor Dashboard message metrics denied", exception);
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Message Service dashboard metrics request failed", exception);
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Message Service is unavailable", exception);
        } catch (ResourceAccessException exception) {
            throw DownstreamRestClientErrors.mapResourceAccess(
                    exception,
                    "Message Service dashboard metrics request timed out",
                    "Unable to reach Message Service"
            );
        } catch (RestClientException exception) {
            throw DownstreamRestClientErrors.mapRestClient(exception, "Unable to reach Message Service");
        }
    }

    public record MessageMetricsRequest(
            UUID viewerUserId,
            List<UUID> questionChannelIds,
            List<UUID> cohortIds,
            List<UUID> courseIds
    ) {
    }

    private record MessageMetricsBody(
            List<UUID> questionChannelIds,
            List<UUID> cohortIds,
            List<UUID> courseIds
    ) {
    }

    public record MessageMetricsResponse(
            int unansweredSupportQuestions,
            int openTaQueueItems,
            int approvedFaqCount,
            int faqCandidateGroups
    ) {
    }
}
