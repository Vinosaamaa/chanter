package com.chanter.notification.infra;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.notification.application.NotificationVisibility;
import com.chanter.notification.domain.Notification;
import java.util.Objects;
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
            case "STUDY_ASSISTANT_ANSWER" -> notification.channelId()==null ? null : "/api/v1/course-channels/"+notification.channelId()+"/accepted-answers/"+notification.sourceId();
            case "COMMUNITY_EVENT" -> notification.studyServerId() == null ? null : "/api/v1/study-servers/" + notification.studyServerId() + "/events/" + notification.sourceId();
            case "ANNOUNCEMENT" -> notification.studyServerId() == null ? null : "/api/v1/study-servers/" + notification.studyServerId() + "/announcements/" + notification.sourceId();
            case "OFFICE_HOURS" -> "/api/v1/office-hours/" + notification.sourceId();
            default -> null;
        };
        if (path == null) return false;
        try {
            Source source = (java.util.Set.of("SUPPORT_QUESTION","STUDY_ASSISTANT_ANSWER").contains(notification.sourceType()) ? message : community).get().uri(path)
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, token).header(AuthHeaders.USER_ID, notification.userId().toString())
                    .retrieve().body(Source.class);
            return source != null && notification.sourceId().equals(source.id())
                    && (!notification.sourceType().equals("COMMUNITY_EVENT")
                        || (Objects.equals(source.courseId(), notification.courseId()) && Objects.equals(source.cohortId(), notification.cohortId())))
                    && !"CANCELLED".equals(source.status()) && !"ARCHIVED".equals(source.status());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode().value() == 403 || exception.getStatusCode().value() == 404 || exception.getStatusCode().value()==410) return false;
            throw unavailable(exception);
        } catch (RestClientException exception) { throw unavailable(exception); }
    }
    private static RestClient client(String base) {
        var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().baseUrl(base).requestFactory(factory).build();
    }
    private record Source(java.util.UUID id, java.util.UUID courseId, java.util.UUID cohortId, String status) { }
    private static ResponseStatusException unavailable(Exception cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Notification access is temporarily unavailable", cause);
    }
}
