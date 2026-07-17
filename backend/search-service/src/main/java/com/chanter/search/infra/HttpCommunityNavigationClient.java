package com.chanter.search.infra;

import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.search.application.CommunityNavigationClient;
import com.chanter.search.config.CommunityServiceClientProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!test")
public class HttpCommunityNavigationClient implements CommunityNavigationClient {

    private final RestClient restClient;
    private final String internalServiceToken;

    public HttpCommunityNavigationClient(
            CommunityServiceClientProperties properties,
            @Value("${chanter.internal-service-token}") String internalServiceToken
    ) {
        this.restClient = DownstreamRestClientFactory.create(
                properties.baseUrl(),
                properties.connectTimeout(),
                properties.readTimeout()
        );
        this.internalServiceToken = InternalServiceTokens.require(internalServiceToken);
    }

    @Override
    public StudyServerNavigation fetchNavigation(UUID studyServerId, UUID viewerUserId) {
        try {
            NavigationResponse response = restClient.get()
                    .uri("/api/v1/study-servers/{studyServerId}/navigation", studyServerId)
                    .header(AuthHeaders.USER_ID, viewerUserId.toString())
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, internalServiceToken)
                    .retrieve()
                    .body(NavigationResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service returned empty navigation");
            }

            List<CourseSummary> courses = response.courses() == null
                    ? List.of()
                    : response.courses().stream()
                            .map(course -> new CourseSummary(course.id(), course.title()))
                            .toList();

            return new StudyServerNavigation(
                    response.studyServerId(),
                    response.studyServerName(),
                    response.canViewFullCatalog(),
                    courses
            );
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found", exception);
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Study Server access denied", exception);
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service navigation request failed", exception);
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service is unavailable", exception);
        } catch (ResourceAccessException exception) {
            throw DownstreamRestClientErrors.mapResourceAccess(
                    exception,
                    "Community Service navigation request timed out",
                    "Unable to reach Community Service"
            );
        } catch (RestClientException exception) {
            throw DownstreamRestClientErrors.mapRestClient(exception, "Unable to reach Community Service");
        }
    }

    private record NavigationResponse(
            UUID studyServerId,
            String studyServerName,
            boolean canViewFullCatalog,
            List<CourseResponse> courses
    ) {
    }

    private record CourseResponse(UUID id, String title) {
    }
}
