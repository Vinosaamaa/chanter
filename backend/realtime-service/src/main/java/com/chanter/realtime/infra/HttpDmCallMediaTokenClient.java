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
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
@Profile("!test")
public class HttpDmCallMediaTokenClient implements DmCallMediaTokenClient {

    private final WebClient webClient;
    private final String serviceToken;

    public HttpDmCallMediaTokenClient(
            @Value("${chanter.community-service.base-url:http://localhost:8082}") String communityServiceBaseUrl,
            @Value("${chanter.community-service.service-token}") String serviceToken
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(communityServiceBaseUrl)
                .build();
        this.serviceToken = serviceToken;
    }

    @Override
    public Mono<DmCallMediaToken> issueForCall(
            UUID callId,
            UUID participantUserId,
            UUID callerUserId,
            UUID calleeUserId
    ) {
        return webClient.post()
                .uri("/internal/v1/dm-calls/{callId}/media-token", callId)
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, serviceToken)
                .header(AuthHeaders.USER_ID, participantUserId.toString())
                .header("X-Dm-Call-Caller-Id", callerUserId.toString())
                .header("X-Dm-Call-Callee-Id", calleeUserId.toString())
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Community Service returned an empty token"
                )))
                .map(response -> new DmCallMediaToken(
                        response.roomName(),
                        response.serverUrl(),
                        response.participantToken(),
                        response.canSpeak(),
                        response.canListen()
                ))
                .onErrorMap(WebClientResponseException.class, exception -> new ResponseStatusException(
                        HttpStatus.valueOf(exception.getStatusCode().value()),
                        exception.getResponseBodyAsString(),
                        exception
                ))
                .onErrorMap(WebClientRequestException.class, exception -> new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Community Service is unavailable",
                        exception
                ));
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
