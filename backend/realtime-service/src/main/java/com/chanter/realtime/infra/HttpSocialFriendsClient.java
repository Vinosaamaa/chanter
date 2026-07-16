package com.chanter.realtime.infra;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.realtime.application.SocialFriendsClient;
import java.util.List;
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
public class HttpSocialFriendsClient implements SocialFriendsClient {

    private final WebClient webClient;
    private final String internalServiceToken;

    public HttpSocialFriendsClient(
            @Value("${chanter.message-service.base-url:http://localhost:8083}") String messageServiceBaseUrl,
            @Value("${chanter.internal-service-token}") String internalServiceToken
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(messageServiceBaseUrl)
                .build();
        this.internalServiceToken = internalServiceToken;
    }

    @Override
    public Mono<List<UUID>> listFriendUserIds(UUID viewerUserId) {
        return webClient.get()
                .uri("/api/v1/friendships")
                .header(AuthHeaders.USER_ID, viewerUserId.toString())
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, internalServiceToken)
                .retrieve()
                .bodyToMono(FriendsListResponse.class)
                .map(response -> {
                    if (response == null || response.friends() == null) {
                        return List.<UUID>of();
                    }
                    return response.friends().stream()
                            .map(FriendSummaryResponse::friendUserId)
                            .toList();
                })
                .onErrorMap(WebClientResponseException.class, exception -> new ResponseStatusException(
                        HttpStatus.valueOf(exception.getStatusCode().value()),
                        exception.getResponseBodyAsString(),
                        exception
                ))
                .onErrorResume(exception -> exception instanceof ResponseStatusException
                        ? Mono.error(exception)
                        : Mono.just(List.of()));
    }

    private record FriendsListResponse(List<FriendSummaryResponse> friends) {
    }

    private record FriendSummaryResponse(UUID friendUserId) {
    }
}
