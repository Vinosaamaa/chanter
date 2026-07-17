package com.chanter.notification.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.notification.application.NotificationRepository;
import com.chanter.notification.application.NotificationService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/internal/notifications")
public class InternalNotificationController {

    private final NotificationService notificationService;
    private final byte[] internalServiceToken;

    public InternalNotificationController(
            NotificationService notificationService,
            @Value("${chanter.internal-service-token}") String internalServiceToken
    ) {
        this.notificationService = notificationService;
        this.internalServiceToken = InternalServiceTokens.requireBytes(internalServiceToken);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse create(
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String serviceToken,
            @Valid @RequestBody CreateNotificationRequest request
    ) {
        requireInternalService(serviceToken);
        return NotificationResponse.from(notificationService.create(new NotificationRepository.CreateCommand(
                request.userId(),
                request.kind(),
                request.filterBucket(),
                request.title(),
                request.bodyPreview(),
                request.courseLabel(),
                request.href(),
                request.sourceType(),
                request.sourceId(),
                request.studyServerId(),
                request.courseId(),
                request.cohortId(),
                request.channelId()
        )));
    }

    private void requireInternalService(String presentedToken) {
        byte[] presented = presentedToken == null
                ? new byte[0]
                : presentedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(internalServiceToken, presented)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal service authentication required");
        }
    }
}
