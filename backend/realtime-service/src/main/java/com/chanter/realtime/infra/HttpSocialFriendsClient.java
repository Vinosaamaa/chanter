package com.chanter.realtime.infra;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.realtime.application.SocialFriendsClient;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Profile("!test")
public class HttpSocialFriendsClient implements SocialFriendsClient {

    private final WebClient webClient;

    public HttpSocialFriendsClient(
            @Value("${chanter.message-service.base-url:http://localhost:8083}") String messageServiceBaseUrl
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(messageServiceBaseUrl)
                .build();
    }

    @Override
    public List<UUID> listFriendUserIds(UUID viewerUserId) {
        FriendsListResponse response = webClient.get()
                .uri("/api/v1/friendships")
                .header(AuthHeaders.USER_ID, viewerUserId.toString())
                .retrieve()
                .bodyToMono(FriendsListResponse.class)
                .block();

        if (response == null || response.friends() == null) {
            return List.of();
        }

        return response.friends().stream()
                .map(FriendSummaryResponse::friendUserId)
                .toList();
    }

    private record FriendsListResponse(List<FriendSummaryResponse> friends) {
    }

    private record FriendSummaryResponse(UUID friendUserId) {
    }
}
