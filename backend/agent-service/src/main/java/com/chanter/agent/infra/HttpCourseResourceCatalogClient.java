package com.chanter.agent.infra;

import com.chanter.agent.application.CourseResourceCatalogClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
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
public class HttpCourseResourceCatalogClient implements CourseResourceCatalogClient {

    private final RestClient restClient;

    public HttpCourseResourceCatalogClient(
            @Value("${chanter.media-service.base-url:http://localhost:8084}") String mediaServiceBaseUrl,
            @Value("${chanter.media-service.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${chanter.media-service.read-timeout-seconds:10}") int readTimeoutSeconds
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
    }

    @Override
    public List<CourseResourceSummary> listAiApprovedCourseResources(UUID courseId, UUID viewerUserId) {
        try {
            CourseResourceListResponse response = restClient.get()
                    .uri("/api/v1/courses/{courseId}/course-resources?viewerUserId={viewerUserId}", courseId, viewerUserId)
                    .retrieve()
                    .body(CourseResourceListResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Media Service returned empty course resources");
            }

            return response.courseResources().stream()
                    .filter(CourseResourceResponse::aiApproved)
                    .map(resource -> new CourseResourceSummary(
                            resource.id(),
                            resource.courseId(),
                            resource.title(),
                            resource.fileName(),
                            resource.aiApproved()
                    ))
                    .toList();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        } catch (HttpClientErrorException.Forbidden exception) {
            return List.of();
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Media Service rejected the course resources request");
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Media Service is unavailable");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Media Service");
        }
    }

    private record CourseResourceListResponse(List<CourseResourceResponse> courseResources) {
    }

    private record CourseResourceResponse(
            UUID id,
            UUID courseId,
            String title,
            String fileName,
            boolean aiApproved
    ) {
    }
}
