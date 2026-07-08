package com.chanter.message.infra;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.message.application.CoMembershipClient;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("!test")
public class HttpCoMembershipClient implements CoMembershipClient {

    private final RestClient restClient;

    public HttpCoMembershipClient(
            @Value("${chanter.community-service.base-url:http://localhost:8082}") String communityServiceBaseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(communityServiceBaseUrl)
                .build();
    }

    @Override
    public boolean shareStudyServerMembership(UUID firstUserId, UUID secondUserId) {
        CoMembershipResponse response = restClient.get()
                .uri("/api/v1/users/{peerUserId}/co-membership", secondUserId)
                .header(AuthHeaders.USER_ID, firstUserId.toString())
                .retrieve()
                .body(CoMembershipResponse.class);

        return response != null && response.coMembers();
    }

    private record CoMembershipResponse(boolean coMembers) {
    }
}
