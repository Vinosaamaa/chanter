package com.chanter.message.infra;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.message.application.CoMembershipClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!test")
public class HttpCoMembershipClient implements CoMembershipClient {

    private final RestClient restClient;

    public HttpCoMembershipClient(
            @Value("${chanter.community-service.base-url:http://localhost:8082}") String communityServiceBaseUrl,
            @Value("${chanter.community-service.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${chanter.community-service.read-timeout-seconds:10}") int readTimeoutSeconds
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                        .build()
        );
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restClient = RestClient.builder()
                .baseUrl(communityServiceBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean shareStudyServerMembership(UUID firstUserId, UUID secondUserId) {
        CoMembershipResponse response = restClient.get()
                .uri("/api/v1/users/{peerUserId}/co-membership", secondUserId)
                .header(AuthHeaders.USER_ID, firstUserId.toString())
                .retrieve()
                .body(CoMembershipResponse.class);

        if (response == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Community Service returned an empty co-membership response"
            );
        }

        return response.coMembers();
    }

    private record CoMembershipResponse(boolean coMembers) {
    }
}
