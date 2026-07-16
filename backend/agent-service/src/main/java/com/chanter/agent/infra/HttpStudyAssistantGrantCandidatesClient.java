package com.chanter.agent.infra;

import com.chanter.agent.application.StudyAssistantGrantCandidatesClient;
import com.chanter.common.auth.AuthHeaders;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Set;
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
public class HttpStudyAssistantGrantCandidatesClient implements StudyAssistantGrantCandidatesClient {

    private final RestClient restClient;
    private final String internalServiceToken;

    public HttpStudyAssistantGrantCandidatesClient(
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
    public GrantCandidates requireGrantCandidates(UUID studyServerId, UUID userId) {
        try {
            GrantCandidatesResponse response = restClient.get()
                    .uri("/api/v1/study-servers/{studyServerId}/study-assistant-grant-candidates", studyServerId)
                    .header(AuthHeaders.USER_ID, userId.toString())
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, internalServiceToken)
                    .retrieve()
                    .body(GrantCandidatesResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service returned empty grant candidates");
            }

            return mapGrantCandidates(response);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found", exception);
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Grant candidates require Study Server Owner or Course Instructor role",
                    exception
            );
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Community Service rejected the grant candidates request",
                    exception
            );
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service is unavailable", exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Community Service", exception);
        }
    }

    @Override
    public ViewerScope requireViewerScope(UUID studyServerId, UUID userId) {
        try {
            ViewerScopeResponse response = restClient.get()
                    .uri("/api/v1/study-servers/{studyServerId}/study-assistant-viewer-scope", studyServerId)
                    .header(AuthHeaders.USER_ID, userId.toString())
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, internalServiceToken)
                    .retrieve()
                    .body(ViewerScopeResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service returned empty viewer scope");
            }

            return new ViewerScope(
                    response.studyServerId(),
                    response.canViewAllGrants(),
                    Set.copyOf(nullToEmpty(response.enrolledCourseIds())),
                    Set.copyOf(nullToEmpty(response.enrolledCohortIds())),
                    Set.copyOf(nullToEmpty(response.accessibleCourseChannelIds()))
            );
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found", exception);
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Study Assistant presence requires Study Server membership or enrollment",
                    exception
            );
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Community Service rejected the viewer scope request",
                    exception
            );
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service is unavailable", exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Community Service", exception);
        }
    }

    private static GrantCandidates mapGrantCandidates(GrantCandidatesResponse response) {
        List<ChannelCandidate> studyServerChannels = nullToEmpty(response.studyServerChannels()).stream()
                .map(channel -> new ChannelCandidate(channel.id(), channel.name(), channel.kind()))
                .toList();

        List<CourseCandidate> courses = nullToEmpty(response.courses()).stream()
                .map(course -> new CourseCandidate(
                        course.id(),
                        course.title(),
                        nullToEmpty(course.cohorts()).stream()
                                .map(cohort -> new CohortCandidate(cohort.id(), cohort.name()))
                                .toList(),
                        nullToEmpty(course.channels()).stream()
                                .map(channel -> new ChannelCandidate(channel.id(), channel.name(), channel.kind()))
                                .toList()
                ))
                .toList();

        return new GrantCandidates(response.studyServerId(), studyServerChannels, courses);
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values != null ? values : List.of();
    }

    private record GrantCandidatesResponse(
            UUID studyServerId,
            List<ChannelResponse> studyServerChannels,
            List<CourseResponse> courses
    ) {
    }

    private record ChannelResponse(UUID id, String name, String kind) {
    }

    private record CohortResponse(UUID id, String name) {
    }

    private record CourseResponse(
            UUID id,
            String title,
            List<CohortResponse> cohorts,
            List<ChannelResponse> channels
    ) {
    }

    private record ViewerScopeResponse(
            UUID studyServerId,
            boolean canViewAllGrants,
            List<UUID> enrolledCourseIds,
            List<UUID> enrolledCohortIds,
            List<UUID> accessibleCourseChannelIds
    ) {
    }
}
