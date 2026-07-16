package com.chanter.agent.infra;

import com.chanter.agent.application.StudyServerSaasPlanClient;
import com.chanter.common.auth.AuthHeaders;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!test")
public class HttpStudyServerSaasPlanClient implements StudyServerSaasPlanClient {

    private final RestClient restClient;
    private final String internalServiceToken;

    public HttpStudyServerSaasPlanClient(
            @Value("${chanter.community-service.base-url:http://localhost:8082}") String communityServiceBaseUrl,
            @Value("${chanter.community-service.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${chanter.community-service.read-timeout-seconds:10}") int readTimeoutSeconds,
            @Value("${chanter.internal-service-token}") String internalServiceToken
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                        .build()
        );
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(communityServiceBaseUrl)
                .requestFactory(requestFactory)
                .build();
        this.internalServiceToken = internalServiceToken;
    }

    @Override
    public StudyServerSaasPlan fetchPlan(UUID studyServerId, UUID actingUserId) {
        try {
            SaasPlanResponse response = restClient.get()
                    .uri("/api/v1/study-servers/{studyServerId}/saas-plan", studyServerId)
                    .header(AuthHeaders.USER_ID, actingUserId.toString())
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, internalServiceToken)
                    .retrieve()
                    .body(SaasPlanResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service returned empty SaaS Plan");
            }

            return new StudyServerSaasPlan(
                    response.studyServerId(),
                    response.planTier(),
                    response.aiInvocationLimit()
            );
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found", exception);
        } catch (HttpClientErrorException | HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service SaaS Plan request failed", exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Community Service", exception);
        }
    }

    private record SaasPlanResponse(UUID studyServerId, String planTier, int aiInvocationLimit) {
    }
}
