package com.chanter.realtime.infra;

import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.realtime.application.DirectMessageClient;
import com.chanter.realtime.application.PersistedDirectMessage;
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
public class HttpDirectMessageClient implements DirectMessageClient {

    private final WebClient webClient;
    private final String internalServiceToken;

    public HttpDirectMessageClient(
            @Value("${chanter.message-service.base-url:http://localhost:8083}") String messageServiceBaseUrl,
            @Value("${chanter.internal-service-token}") String internalServiceToken
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(messageServiceBaseUrl)
                .build();
        this.internalServiceToken = InternalServiceTokens.require(internalServiceToken);
    }

    @Override
    public PersistedDirectMessage sendDirectMessage(UUID senderUserId, UUID recipientUserId, String body) {
        try {
            DirectMessageResponse response = webClient.post()
                    .uri("/api/v1/direct-messages")
                    .header(AuthHeaders.USER_ID, senderUserId.toString())
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, internalServiceToken)
                    .bodyValue(Map.of(
                            "recipientUserId", recipientUserId.toString(),
                            "body", body
                    ))
                    .retrieve()
                    .bodyToMono(DirectMessageResponse.class)
                    .block();

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Message Service returned an empty response");
            }

            return new PersistedDirectMessage(
                    response.id(),
                    response.senderUserId(),
                    response.recipientUserId(),
                    response.body(),
                    response.sentAt()
            );
        } catch (WebClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.valueOf(exception.getStatusCode().value()),
                    exception.getResponseBodyAsString()
            );
        }
    }

    private record DirectMessageResponse(
            UUID id,
            UUID senderUserId,
            UUID recipientUserId,
            String body,
            Instant sentAt
    ) {
    }
}
