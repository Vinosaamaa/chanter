package com.chanter.notification.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.notification.application.NotificationService;
import com.chanter.notification.domain.NotificationListFilter;
import com.chanter.notification.domain.NotificationListStatus;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/me/notifications")
public class MeNotificationController {

    private final NotificationService notificationService;

    public MeNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationListResponse list(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId,
            @RequestParam(defaultValue = "ALL") NotificationListFilter filter,
            @RequestParam(defaultValue = "OPEN") NotificationListStatus status
    ) {
        return new NotificationListResponse(
                notificationService.list(userId, filter, status).stream()
                        .map(NotificationResponse::from)
                        .toList()
        );
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return new UnreadCountResponse(notificationService.unreadCount(userId));
    }

    @PostMapping("/{notificationId}/read")
    public NotificationResponse markRead(
            @PathVariable UUID notificationId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return NotificationResponse.from(notificationService.markRead(notificationId, userId));
    }

    @PostMapping("/{notificationId}/done")
    public NotificationResponse markDone(
            @PathVariable UUID notificationId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return NotificationResponse.from(notificationService.markDone(notificationId, userId));
    }
}
