package com.chanter.notification.infra;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.notification.application.NotificationVisibility;
import com.chanter.notification.domain.Notification;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpNotificationVisibility implements NotificationVisibility {
    private final RestClient community;
    private final RestClient message;
    private final String token;
    public HttpNotificationVisibility(@Value("${COMMUNITY_SERVICE_URL:http://localhost:8082}") String community,
            @Value("${MESSAGE_SERVICE_URL:http://localhost:8083}") String message,
            @Value("${chanter.internal-service-token}") String token) {
        this.community = client(community);
        this.message = client(message);
        this.token = InternalServiceTokens.require(token);
    }
    @Override public boolean canView(Notification notification) {
        String path = switch (notification.sourceType()) {
            case "SUPPORT_QUESTION" -> notification.channelId() == null ? null : "/api/v1/course-channels/" + notification.channelId() + "/support-questions/" + notification.sourceId();
            case "COMMUNITY_EVENT" -> notification.studyServerId() == null ? null : "/api/v1/study-servers/" + notification.studyServerId() + "/events/" + notification.sourceId();
            case "ANNOUNCEMENT" -> notification.studyServerId() == null ? null : "/api/v1/study-servers/" + notification.studyServerId() + "/announcements/" + notification.sourceId();
            case "OFFICE_HOURS" -> "/api/v1/office-hours/" + notification.sourceId();
            default -> null;
        };
        if (path == null) return false;
        try {
            JsonNode source = (notification.sourceType().equals("SUPPORT_QUESTION") ? message : community).get().uri(path)
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, token).header(AuthHeaders.USER_ID, notification.userId().toString())
                    .retrieve().body(JsonNode.class);
            return source != null && source.path("id").asText().equals(notification.sourceId().toString())
                    && !java.util.Set.of("CANCELLED", "ARCHIVED").contains(source.path("status").asText());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode().value() == 403 || exception.getStatusCode().value() == 404) return false;
            throw unavailable(exception);
        } catch (RestClientException exception) { throw unavailable(exception); }
    }
    private static RestClient client(String base) {
        var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().baseUrl(base).requestFactory(factory).build();
    }
    private static ResponseStatusException unavailable(Exception cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Notification access is temporarily unavailable", cause);
    }
}
