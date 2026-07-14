package com.chanter.community.infra;

import com.chanter.community.application.AuthUserDirectoryClient;
import com.chanter.community.config.AuthServiceClientProperties;
import com.chanter.community.domain.AuthUserProfile;
import com.chanter.common.auth.AuthHeaders;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!test")
public class HttpAuthUserDirectoryClient implements AuthUserDirectoryClient {

    private static final int MAX_PROFILE_QUERY_SIZE = 100;

    private final RestClient restClient;
    private final String serviceToken;

    @Autowired
    public HttpAuthUserDirectoryClient(AuthServiceClientProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build()
        );
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.serviceToken = properties.serviceToken();
    }

    HttpAuthUserDirectoryClient(RestClient restClient, String serviceToken) {
        this.restClient = restClient;
        this.serviceToken = serviceToken;
    }

    @Override
    public Optional<AuthUserProfile> findByEmail(String email) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/v1/users/by-email")
                            .queryParam("email", email)
                            .build())
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, serviceToken)
                    .retrieve()
                    .body(AuthUserProfile.class));
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public List<AuthUserProfile> findByIds(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        List<UUID> distinctUserIds = userIds.stream().distinct().toList();
        List<AuthUserProfile> profiles = new ArrayList<>();
        for (int start = 0; start < distinctUserIds.size(); start += MAX_PROFILE_QUERY_SIZE) {
            int end = Math.min(start + MAX_PROFILE_QUERY_SIZE, distinctUserIds.size());
            profiles.addAll(findProfileBatch(distinctUserIds.subList(start, end)));
        }
        return List.copyOf(profiles);
    }

    private List<AuthUserProfile> findProfileBatch(List<UUID> userIds) {
        try {
            AuthUserProfileList response = restClient.post()
                    .uri("/internal/v1/users/profiles/query")
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, serviceToken)
                    .body(new AuthUserProfileQuery(userIds))
                    .retrieve()
                    .body(AuthUserProfileList.class);
            return response == null ? List.of() : response.profiles();
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private static ResponseStatusException unavailable(RestClientException exception) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Auth user directory is unavailable", exception);
    }

    private record AuthUserProfileQuery(List<UUID> userIds) {
    }

    private record AuthUserProfileList(List<AuthUserProfile> profiles) {
    }
}
