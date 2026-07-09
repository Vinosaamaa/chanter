package com.chanter.realtime.infra;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.realtime.application.DmCallMediaToken;
import com.chanter.realtime.application.DmCallMediaTokenClient;
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
public class HttpDmCallMediaTokenClient implements DmCallMediaTokenClient {

    private final WebClient webClient;

    public HttpDmCallMediaTokenClient(
            @Value("${chanter.community-service.base-url:http://localhost:8082}") String communityServiceBaseUrl
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(communityServiceBaseUrl)
                .build();
    }

    @Override
    public DmCallMediaToken issueForCall(UUID callId, UUID participantUserId) {
        try {
            TokenResponse response = webClient.post()
                    .uri("/internal/v1/dm-calls/{callId}/media-token", callId)
                    .header(AuthHeaders.USER_ID, participantUserId.toString())
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Community Service returned an empty token");
            }

            return new DmCallMediaToken(
                    response.roomName(),
                    response.serverUrl(),
                    response.participantToken(),
                    response.canSpeak(),
                    response.canListen()
            );
        } catch (WebClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.valueOf(exception.getStatusCode().value()),
                    exception.getResponseBodyAsString(),
                    exception
            );
        }
    }

    private record TokenResponse(
            String roomName,
            String serverUrl,
            String participantToken,
            boolean canSpeak,
            boolean canListen
    ) {
    }
}
