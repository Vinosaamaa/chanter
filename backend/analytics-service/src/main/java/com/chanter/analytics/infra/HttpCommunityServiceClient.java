package com.chanter.analytics.infra;

import com.chanter.analytics.config.CommunityServiceClientProperties;
import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpCommunityServiceClient {

    private final RestClient restClient;

    public HttpCommunityServiceClient(
            CommunityServiceClientProperties properties,
            HttpClient analyticsHttpClient
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                        analyticsHttpClient,
                        properties.connectTimeout()
                ))
                .build();
    }

    public GrantCandidatesResponse fetchGrantCandidates(UUID studyServerId, UUID viewerUserId) {
        try {
            GrantCandidatesResponse response = restClient.get()
                    .uri("/api/v1/study-servers/{studyServerId}/study-assistant-grant-candidates?userId={userId}",
                            studyServerId, viewerUserId)
                    .retrieve()
                    .body(GrantCandidatesResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service returned empty grant candidates");
            }

            return response;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found", exception);
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Instructor Dashboard requires Study Server Owner or Course Instructor role",
                    exception
            );
        }
    }

    public CommunityMetricsResponse fetchCommunityMetrics(UUID studyServerId, UUID viewerUserId) {
        try {
            CommunityMetricsResponse response = restClient.get()
                    .uri("/api/v1/study-servers/{studyServerId}/instructor-dashboard/community-metrics?userId={userId}",
                            studyServerId, viewerUserId)
                    .retrieve()
                    .body(CommunityMetricsResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service returned empty community metrics");
            }

            return response;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found", exception);
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Instructor Dashboard requires Study Server Owner or Course Instructor role",
                    exception
            );
        }
    }

    public record GrantCandidatesResponse(
            UUID studyServerId,
            List<ChannelResponse> studyServerChannels,
            List<CourseResponse> courses
    ) {
    }

    public record ChannelResponse(UUID id, String name, String kind) {
    }

    public record CohortResponse(UUID id, String name) {
    }

    public record CourseResponse(
            UUID id,
            String title,
            List<CohortResponse> cohorts,
            List<ChannelResponse> channels
    ) {
    }

    public record CommunityMetricsResponse(
            UUID studyServerId,
            int liveOfficeHoursSessions,
            int scheduledOfficeHoursSessions,
            int officeHoursWaitlistEntries
    ) {
    }
}
