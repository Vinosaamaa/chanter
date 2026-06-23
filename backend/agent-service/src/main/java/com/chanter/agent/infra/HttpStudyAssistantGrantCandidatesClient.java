package com.chanter.agent.infra;

import com.chanter.agent.application.StudyAssistantGrantCandidatesClient;
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

    public HttpStudyAssistantGrantCandidatesClient(
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
    public GrantCandidates requireGrantCandidates(UUID studyServerId, UUID userId) {
        try {
            GrantCandidatesResponse response = restClient.get()
                    .uri(
                            "/api/v1/study-servers/{studyServerId}/study-assistant-grant-candidates?userId={userId}",
                            studyServerId,
                            userId
                    )
                    .retrieve()
                    .body(GrantCandidatesResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service returned empty grant candidates");
            }

            return mapGrantCandidates(response);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Grant candidates require Study Server Owner or Course Instructor role"
            );
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service rejected the grant candidates request");
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service is unavailable");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Community Service");
        }
    }

    @Override
    public ViewerScope requireViewerScope(UUID studyServerId, UUID userId) {
        try {
            ViewerScopeResponse response = restClient.get()
                    .uri(
                            "/api/v1/study-servers/{studyServerId}/study-assistant-viewer-scope?userId={userId}",
                            studyServerId,
                            userId
                    )
                    .retrieve()
                    .body(ViewerScopeResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service returned empty viewer scope");
            }

            return new ViewerScope(
                    response.studyServerId(),
                    response.canViewAllGrants(),
                    Set.copyOf(response.enrolledCourseIds()),
                    Set.copyOf(response.enrolledCohortIds()),
                    Set.copyOf(response.accessibleCourseChannelIds())
            );
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Study Assistant presence requires Study Server membership or enrollment"
            );
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service rejected the viewer scope request");
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service is unavailable");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Community Service");
        }
    }

    private static GrantCandidates mapGrantCandidates(GrantCandidatesResponse response) {
        List<ChannelCandidate> studyServerChannels = response.studyServerChannels().stream()
                .map(channel -> new ChannelCandidate(channel.id(), channel.name(), channel.kind()))
                .toList();

        List<CourseCandidate> courses = response.courses().stream()
                .map(course -> new CourseCandidate(
                        course.id(),
                        course.title(),
                        course.cohorts().stream()
                                .map(cohort -> new CohortCandidate(cohort.id(), cohort.name()))
                                .toList(),
                        course.channels().stream()
                                .map(channel -> new ChannelCandidate(channel.id(), channel.name(), channel.kind()))
                                .toList()
                ))
                .toList();

        return new GrantCandidates(response.studyServerId(), studyServerChannels, courses);
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
