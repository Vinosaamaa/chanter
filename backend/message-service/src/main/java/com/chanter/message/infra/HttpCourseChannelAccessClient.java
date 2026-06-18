package com.chanter.message.infra;

import com.chanter.message.application.CourseChannelAccess;
import com.chanter.message.application.CourseChannelAccessClient;
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
public class HttpCourseChannelAccessClient implements CourseChannelAccessClient {

    private final RestClient restClient;

    public HttpCourseChannelAccessClient(
            @Value("${chanter.community-service.base-url:http://localhost:8082}") String communityServiceBaseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(communityServiceBaseUrl)
                .build();
    }

    @Override
    public CourseChannelAccess requireAccess(UUID channelId, UUID userId) {
        try {
            AccessResponse response = restClient.get()
                    .uri("/api/v1/course-channels/{channelId}/support-question-access?userId={userId}",
                            channelId, userId)
                    .retrieve()
                    .body(AccessResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service returned an empty access response");
            }

            return new CourseChannelAccess(
                    response.channelId(),
                    response.courseId(),
                    response.channelName(),
                    response.canPostSupportQuestion(),
                    response.canViewUnansweredSupportQuestions()
            );
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Channel not found");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Course Channel access requires Cohort Enrollment or Instructor role");
        }
    }

    private record AccessResponse(
            UUID channelId,
            UUID courseId,
            String channelName,
            boolean canPostSupportQuestion,
            boolean canViewUnansweredSupportQuestions
    ) {
    }
}
