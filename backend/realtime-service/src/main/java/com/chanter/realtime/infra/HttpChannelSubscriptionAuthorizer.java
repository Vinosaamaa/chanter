package com.chanter.realtime.infra;

import com.chanter.realtime.application.ChannelSubscriptionAuthorizer;
import com.chanter.realtime.domain.RealtimeChannelScope;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!test")
public class HttpChannelSubscriptionAuthorizer implements ChannelSubscriptionAuthorizer {

    private final WebClient webClient;

    public HttpChannelSubscriptionAuthorizer(
            @Value("${chanter.community-service.base-url:http://localhost:8082}") String communityServiceBaseUrl
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(communityServiceBaseUrl)
                .build();
    }

    @Override
    public void requireSubscribeAccess(UUID channelId, UUID userId, RealtimeChannelScope channelScope) {
        try {
            String path = channelScope == RealtimeChannelScope.STUDY_SERVER
                    ? "/api/v1/study-server-channels/{channelId}/channel-message-access"
                    : "/api/v1/course-channels/{channelId}/channel-message-access";

            AccessResponse response = webClient.get()
                    .uri(path, channelId)
                    .header("X-User-Id", userId.toString())
                    .retrieve()
                    .bodyToMono(AccessResponse.class)
                    .block();

            if (response == null || !response.canReadMessages()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Channel subscription denied");
            }
        } catch (WebClientResponseException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found");
        } catch (WebClientResponseException.Forbidden exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Channel subscription denied");
        } catch (WebClientResponseException.BadRequest exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getResponseBodyAsString());
        } catch (WebClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Community Service rejected channel access: " + exception.getResponseBodyAsString()
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to reach Community Service for channel access",
                    exception
            );
        }
    }

    private record AccessResponse(
            UUID channelId,
            boolean canReadMessages,
            boolean canPostMessages
    ) {
    }
}
