package com.chanter.agent.infra;

import com.chanter.agent.application.CourseResourceContentClient;
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
public class HttpCourseResourceContentClient implements CourseResourceContentClient {

    private final RestClient restClient;
    private final String internalServiceToken;

    public HttpCourseResourceContentClient(
            @Value("${chanter.media-service.base-url:http://localhost:8084}") String mediaServiceBaseUrl,
            @Value("${chanter.media-service.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${chanter.media-service.read-timeout-seconds:10}") int readTimeoutSeconds,
            @Value("${chanter.internal-service-token}") String internalServiceToken
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                        .build()
        );
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(mediaServiceBaseUrl)
                .requestFactory(requestFactory)
                .build();
        this.internalServiceToken = internalServiceToken;
    }

    @Override
    public byte[] downloadContent(UUID resourceId, UUID viewerUserId) {
        try {
            byte[] content = restClient.get()
                    .uri("/api/v1/course-resources/{resourceId}/content", resourceId)
                    .header(AuthHeaders.USER_ID, viewerUserId.toString())
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, internalServiceToken)
                    .retrieve()
                    .body(byte[].class);

            if (content == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Media Service returned empty Course Resource content"
                );
            }

            return content;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Resource not found", exception);
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Course Resource access denied", exception);
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Media Service rejected the Course Resource download",
                    exception
            );
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Media Service is unavailable", exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Media Service", exception);
        }
    }
}
