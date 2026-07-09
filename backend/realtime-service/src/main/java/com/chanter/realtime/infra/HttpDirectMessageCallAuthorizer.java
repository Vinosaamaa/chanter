package com.chanter.realtime.infra;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.realtime.application.DirectMessageCallAuthorizer;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
@Profile("!test")
public class HttpDirectMessageCallAuthorizer implements DirectMessageCallAuthorizer {

    private final WebClient webClient;

    public HttpDirectMessageCallAuthorizer(
            @Value("${chanter.message-service.base-url:http://localhost:8083}") String messageServiceBaseUrl
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(messageServiceBaseUrl)
                .build();
    }

    @Override
    public Mono<Void> requireCallAccess(UUID callerUserId, UUID calleeUserId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/direct-message-calls/eligibility")
                        .queryParam("peerUserId", calleeUserId.toString())
                        .build())
                .header(AuthHeaders.USER_ID, callerUserId.toString())
                .retrieve()
                .toBodilessEntity()
                .then()
                .onErrorMap(WebClientResponseException.class, exception -> new ResponseStatusException(
                        HttpStatus.valueOf(exception.getStatusCode().value()),
                        exception.getResponseBodyAsString(),
                        exception
                ));
    }
}
