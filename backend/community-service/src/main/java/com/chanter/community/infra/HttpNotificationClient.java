package com.chanter.community.infra;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.community.application.NotificationClient;
import com.chanter.community.config.NotificationServiceClientProperties;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Profile("!test")
public class HttpNotificationClient implements NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(HttpNotificationClient.class);

    private final RestClient restClient;
    private final String serviceToken;

    public HttpNotificationClient(NotificationServiceClientProperties properties) {
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

    @Override
    public void createNotification(
            UUID userId,
            String kind,
            String title,
            String bodyPreview,
            String courseLabel,
            String href,
            String sourceType,
            UUID sourceId,
            UUID studyServerId,
            UUID courseId,
            UUID cohortId,
            UUID channelId
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("kind", kind);
        body.put("title", title);
        if (bodyPreview != null) {
            body.put("bodyPreview", bodyPreview);
        }
        if (courseLabel != null) {
            body.put("courseLabel", courseLabel);
        }
        body.put("href", href);
        body.put("sourceType", sourceType);
        body.put("sourceId", sourceId);
        if (studyServerId != null) {
            body.put("studyServerId", studyServerId);
        }
        if (courseId != null) {
            body.put("courseId", courseId);
        }
        if (cohortId != null) {
            body.put("cohortId", cohortId);
        }
        if (channelId != null) {
            body.put("channelId", channelId);
        }

        try {
            restClient.post()
                    .uri("/api/v1/internal/notifications")
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, serviceToken)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn(
                    "Failed to create notification kind={} user={} source={}: {}",
                    kind,
                    userId,
                    sourceId,
                    exception.getMessage()
            );
        }
    }
}
