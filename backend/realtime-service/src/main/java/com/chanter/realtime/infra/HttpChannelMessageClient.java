package com.chanter.realtime.infra;

import com.chanter.realtime.application.ChannelMessageClient;
import com.chanter.realtime.application.PersistedChannelMessage;
import com.chanter.realtime.domain.RealtimeChannelScope;
import java.time.Instant;
import java.util.Map;
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
public class HttpChannelMessageClient implements ChannelMessageClient {

    private final WebClient webClient;

    public HttpChannelMessageClient(
            @Value("${chanter.message-service.base-url:http://localhost:8083}") String messageServiceBaseUrl
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(messageServiceBaseUrl)
                .build();
    }

    @Override
    public PersistedChannelMessage postMessage(
            UUID channelId,
            UUID senderUserId,
            RealtimeChannelScope channelScope,
            String body
    ) {
        try {
            String path = channelScope == RealtimeChannelScope.STUDY_SERVER
                    ? "/api/v1/study-server-channels/{channelId}/messages"
                    : "/api/v1/course-channels/{channelId}/messages";

            MessageResponse response = webClient.post()
                    .uri(path, channelId)
                    .header("X-User-Id", senderUserId.toString())
                    .bodyValue(Map.of("body", body))
                    .retrieve()
                    .bodyToMono(MessageResponse.class)
                    .block();

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Message Service returned an empty response");
            }

            return new PersistedChannelMessage(
                    response.id(),
                    response.channelId(),
                    response.senderUserId(),
                    response.body(),
                    response.createdAt()
            );
        } catch (WebClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.valueOf(exception.getStatusCode().value()),
                    exception.getResponseBodyAsString()
            );
        }
    }

    private record MessageResponse(
            UUID id,
            UUID channelId,
            UUID senderUserId,
            String body,
            Instant createdAt
    ) {
    }
}
