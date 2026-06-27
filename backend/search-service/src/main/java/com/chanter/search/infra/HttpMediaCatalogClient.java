package com.chanter.search.infra;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.search.application.MediaCatalogClient;
import com.chanter.search.config.MediaServiceClientProperties;
import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;
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
public class HttpMediaCatalogClient implements MediaCatalogClient {

    private final RestClient restClient;

    public HttpMediaCatalogClient(MediaServiceClientProperties properties) {
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

    @Override
    public List<CourseResourceSummary> listCourseResources(UUID courseId, UUID viewerUserId) {
        try {
            CourseResourceListResponse response = restClient.get()
                    .uri("/api/v1/courses/{courseId}/course-resources", courseId)
                    .header(AuthHeaders.USER_ID, viewerUserId.toString())
                    .retrieve()
                    .body(CourseResourceListResponse.class);

            if (response == null || response.courseResources() == null) {
                return List.of();
            }

            return response.courseResources().stream()
                    .map(resource -> new CourseResourceSummary(
                            resource.id(),
                            resource.courseId(),
                            resource.title(),
                            resource.fileName()
                    ))
                    .toList();
        } catch (HttpClientErrorException.Unauthorized exception) {
            return List.of();
        } catch (HttpClientErrorException.NotFound exception) {
            return List.of();
        } catch (HttpClientErrorException.Forbidden exception) {
            return List.of();
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Media Service rejected the course resources request",
                    exception
            );
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Media Service is unavailable", exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Media Service", exception);
        }
    }

    private record CourseResourceListResponse(List<CourseResourceResponse> courseResources) {
    }

    private record CourseResourceResponse(UUID id, UUID courseId, String title, String fileName) {
    }
}
