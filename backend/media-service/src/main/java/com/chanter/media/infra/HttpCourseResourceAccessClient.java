package com.chanter.media.infra;

import com.chanter.media.application.CourseResourceAccess;
import com.chanter.media.application.CourseResourceAccessClient;
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
public class HttpCourseResourceAccessClient implements CourseResourceAccessClient {

    private final RestClient restClient;

    public HttpCourseResourceAccessClient(
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
    public CourseResourceAccess requireAccess(UUID courseId, UUID userId) {
        try {
            AccessResponse response = restClient.get()
                    .uri("/api/v1/courses/{courseId}/resource-access?userId={userId}", courseId, userId)
                    .retrieve()
                    .body(AccessResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service returned an empty access response");
            }

            return new CourseResourceAccess(
                    response.courseId(),
                    response.canUploadCourseResource(),
                    response.canViewCourseResources()
            );
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Course Resource access requires Cohort Enrollment or Instructor role"
            );
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service rejected the access request");
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service is unavailable");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Community Service");
        }
    }

    private record AccessResponse(
            UUID courseId,
            boolean canUploadCourseResource,
            boolean canViewCourseResources
    ) {
    }
}
