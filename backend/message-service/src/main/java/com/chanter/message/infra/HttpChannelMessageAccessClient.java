package com.chanter.message.infra;

import com.chanter.message.application.ChannelMessageAccess;
import com.chanter.message.application.ChannelMessageAccessClient;
import com.chanter.message.domain.ChannelScope;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!test")
public class HttpChannelMessageAccessClient implements ChannelMessageAccessClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;

    public HttpChannelMessageAccessClient(
            @Value("${chanter.community-service.base-url:http://localhost:8082}") String communityServiceBaseUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(communityServiceBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public ChannelMessageAccess requireAccess(UUID channelId, UUID userId, ChannelScope channelScope) {
        try {
            if (channelScope == ChannelScope.STUDY_SERVER) {
                StudyServerAccessResponse response = restClient.get()
                        .uri("/api/v1/study-server-channels/{channelId}/channel-message-access", channelId)
                        .header("X-User-Id", userId.toString())
                        .retrieve()
                        .body(StudyServerAccessResponse.class);
                return toAccess(channelId, channelScope, response);
            }

            CourseAccessResponse response = restClient.get()
                    .uri("/api/v1/course-channels/{channelId}/channel-message-access", channelId)
                    .header("X-User-Id", userId.toString())
                    .retrieve()
                    .body(CourseAccessResponse.class);
            return toAccess(channelId, channelScope, response);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Channel access denied");
        } catch (HttpClientErrorException.BadRequest exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getResponseBodyAsString());
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Community Service is unavailable",
                    exception
            );
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Community Service request failed",
                    exception
            );
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode().is5xxServerError()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Community Service request failed",
                        exception
                );
            }
            throw exception;
        }
    }

    private static ChannelMessageAccess toAccess(
            UUID channelId,
            ChannelScope channelScope,
            StudyServerAccessResponse response
    ) {
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service returned an empty access response");
        }
        return new ChannelMessageAccess(
                channelId,
                channelScope,
                response.canReadMessages(),
                response.canPostMessages()
        );
    }

    private static ChannelMessageAccess toAccess(
            UUID channelId,
            ChannelScope channelScope,
            CourseAccessResponse response
    ) {
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service returned an empty access response");
        }
        return new ChannelMessageAccess(
                channelId,
                channelScope,
                response.canReadMessages(),
                response.canPostMessages()
        );
    }

    private record StudyServerAccessResponse(
            UUID channelId,
            UUID studyServerId,
            String channelName,
            boolean canReadMessages,
            boolean canPostMessages
    ) {
    }

    private record CourseAccessResponse(
            UUID channelId,
            UUID courseId,
            String channelName,
            boolean canReadMessages,
            boolean canPostMessages
    ) {
    }
}
