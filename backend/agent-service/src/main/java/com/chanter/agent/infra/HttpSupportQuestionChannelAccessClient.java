package com.chanter.agent.infra;

import com.chanter.agent.application.SupportQuestionChannelAccessClient;
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
public class HttpSupportQuestionChannelAccessClient implements SupportQuestionChannelAccessClient {

    private final RestClient restClient;

    public HttpSupportQuestionChannelAccessClient(
            @Value("${chanter.community-service.base-url:http://localhost:8082}") String communityServiceBaseUrl,
            @Value("${chanter.community-service.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${chanter.community-service.read-timeout-seconds:10}") int readTimeoutSeconds
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
    }

    @Override
    public SupportQuestionChannelAccess requireAccess(UUID channelId, UUID userId) {
        try {
            AccessResponse response = restClient.get()
                    .uri("/api/v1/course-channels/{channelId}/support-question-access", channelId)
                    .header(AuthHeaders.USER_ID, userId.toString())
                    .retrieve()
                    .body(AccessResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service returned empty channel access");
            }

            return new SupportQuestionChannelAccess(
                    response.channelId(),
                    response.courseId(),
                    response.studyServerId(),
                    response.channelName(),
                    response.canPostSupportQuestion(),
                    response.canViewUnansweredSupportQuestions()
            );
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Channel not found", exception);
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Course Channel access requires Cohort Enrollment or Instructor role",
                    exception
            );
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Community Service rejected the channel access request",
                    exception
            );
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service is unavailable", exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Community Service", exception);
        }
    }

    private record AccessResponse(
            UUID channelId,
            UUID courseId,
            UUID studyServerId,
            String channelName,
            boolean canPostSupportQuestion,
            boolean canViewUnansweredSupportQuestions
    ) {
    }
}
