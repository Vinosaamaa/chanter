package com.chanter.agent.infra;

import com.chanter.agent.application.SupportQuestionClient;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.auth.AuthHeaders;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!test")
public class HttpSupportQuestionClient implements SupportQuestionClient {

    private final RestClient restClient;
    private final String internalServiceToken;

    public HttpSupportQuestionClient(
            @Value("${chanter.message-service.base-url:http://localhost:8083}") String messageServiceBaseUrl,
            @Value("${chanter.message-service.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${chanter.message-service.read-timeout-seconds:10}") int readTimeoutSeconds,
            @Value("${chanter.internal-service-token}") String internalServiceToken
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                        .build()
        );
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(messageServiceBaseUrl)
                .requestFactory(requestFactory)
                .build();
        this.internalServiceToken = InternalServiceTokens.require(internalServiceToken);
    }

    @Override
    public SupportQuestion getSupportQuestion(UUID channelId, UUID supportQuestionId, UUID viewerUserId) {
        try {
            SupportQuestionResponse response = restClient.get()
                    .uri(
                            "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}",
                            channelId,
                            supportQuestionId
                    )
                    .header(AuthHeaders.USER_ID, viewerUserId.toString())
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, internalServiceToken)
                    .retrieve()
                    .body(SupportQuestionResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Message Service returned empty Support Question");
            }

            return mapSupportQuestion(response);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Support Question not found", exception);
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Support Question access denied", exception);
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Message Service rejected the Support Question request",
                    exception
            );
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Message Service is unavailable", exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Message Service", exception);
        }
    }

    @Override
    public SupportQuestion updateStatus(
            UUID channelId,
            UUID supportQuestionId,
            UUID actorUserId,
            String status
    ) {
        try {
            SupportQuestionResponse response = restClient.patch()
                    .uri("/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/status",
                            channelId, supportQuestionId)
                    .header(AuthHeaders.USER_ID, actorUserId.toString())
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, internalServiceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "status", status
                    ))
                    .retrieve()
                    .body(SupportQuestionResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Message Service returned empty Support Question");
            }

            return mapSupportQuestion(response);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Support Question not found", exception);
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Support Question status update denied", exception);
        } catch (HttpClientErrorException.Conflict exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Support Question is no longer unanswered", exception);
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Message Service rejected the Support Question status update",
                    exception
            );
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Message Service is unavailable", exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Message Service", exception);
        }
    }

    private static SupportQuestion mapSupportQuestion(SupportQuestionResponse response) {
        return new SupportQuestion(
                response.id(),
                response.channelId(),
                response.senderUserId(),
                response.body(),
                response.status(),
                response.createdAt()
        );
    }

    private record SupportQuestionResponse(
            UUID id,
            UUID channelId,
            UUID senderUserId,
            String body,
            String status,
            Instant createdAt
    ) {
    }
}
