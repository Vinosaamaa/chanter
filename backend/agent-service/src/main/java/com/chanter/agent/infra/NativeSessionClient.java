package com.chanter.agent.infra;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.auth.JwtTokenService.AccessSession;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** No cached authority: every native release/acceptance checks the owning auth service. */
@Component
public class NativeSessionClient {
    private final RestClient client;
    private final String serviceToken;
    private final Clock clock;
    public NativeSessionClient(@Value("${chanter.auth-service.base-url:http://localhost:8081}") String baseUrl,
            @Value("${chanter.internal-service-token}") String serviceToken, Clock clock) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.serviceToken = InternalServiceTokens.require(serviceToken); this.clock = clock;
    }
    public AccessSession requireActive(String authorization, UUID expectedUser) {
        if (authorization == null || !authorization.startsWith(AuthHeaders.BEARER_PREFIX)) denied();
        try {
            var session = client.post().uri("/internal/v1/auth/session/introspect")
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, serviceToken).header(AuthHeaders.AUTHORIZATION, authorization)
                    .retrieve().body(AccessSession.class);
            if (session == null || !expectedUser.equals(session.userId()) || session.sessionId() == null
                    || session.expiresAt() == null || !session.expiresAt().isAfter(clock.instant())) denied();
            return session;
        } catch (HttpClientErrorException unauthorized) { denied(); return null; }
        catch (RestClientException unavailable) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Native session verification unavailable"); }
    }
    private static void denied() { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Active browser session required for native access"); }
}
