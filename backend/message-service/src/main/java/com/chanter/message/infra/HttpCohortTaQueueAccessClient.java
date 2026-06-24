package com.chanter.message.infra;

import com.chanter.message.application.CohortTaQueueAccess;
import com.chanter.message.application.CohortTaQueueAccessClient;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!test")
public class HttpCohortTaQueueAccessClient implements CohortTaQueueAccessClient {

    private final RestClient restClient;

    public HttpCohortTaQueueAccessClient(
            @Value("${chanter.community-service.base-url:http://localhost:8082}") String communityServiceBaseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(communityServiceBaseUrl)
                .build();
    }

    @Override
    public CohortTaQueueAccess requireAccess(UUID cohortId, UUID userId) {
        try {
            AccessResponse response = restClient.get()
                    .uri("/api/v1/cohorts/{cohortId}/ta-queue-access?userId={userId}", cohortId, userId)
                    .retrieve()
                    .body(AccessResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service returned an empty TA Queue access response");
            }

            return new CohortTaQueueAccess(
                    response.cohortId(),
                    response.courseId(),
                    response.studyServerId(),
                    response.canAddToTaQueue(),
                    response.canManageTaQueue()
            );
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found", exception);
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "TA Queue access requires Cohort Enrollment or Instructor role",
                    exception
            );
        }
    }

    private record AccessResponse(
            UUID cohortId,
            UUID courseId,
            UUID studyServerId,
            boolean canAddToTaQueue,
            boolean canManageTaQueue
    ) {
    }
}
