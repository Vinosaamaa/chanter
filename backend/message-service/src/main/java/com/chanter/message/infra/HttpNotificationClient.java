package com.chanter.message.infra;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.message.application.NotificationClient;
import com.chanter.message.config.NotificationServiceClientProperties;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
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

    HttpNotificationClient(RestClient restClient, String serviceToken) {
        this.restClient = restClient;
        this.serviceToken = serviceToken;
    }

    @Override
    public void notifySupportQuestionAnswered(
            UUID recipientUserId,
            UUID supportQuestionId,
            UUID channelId,
            UUID courseId,
            String title,
            String bodyPreview,
            String courseLabel
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", recipientUserId);
        body.put("kind", "SUPPORT_QUESTION_ANSWERED");
        body.put("title", title);
        body.put("bodyPreview", bodyPreview);
        body.put("courseLabel", courseLabel);
        body.put("href", "/app/inbox?channelId=" + channelId + "&questionId=" + supportQuestionId);
        body.put("sourceType", "SUPPORT_QUESTION");
        body.put("sourceId", supportQuestionId);
        body.put("courseId", courseId);
        body.put("channelId", channelId);

        try {
            restClient.post()
                    .uri("/api/v1/internal/notifications")
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, serviceToken)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn(
                    "Failed to create support-question notification for user={} question={}: {}",
                    recipientUserId,
                    supportQuestionId,
                    exception.getMessage()
            );
        }
    }
}
