package com.chanter.notification.api;

import java.util.List;

public record NotificationListResponse(List<NotificationResponse> notifications) {
}
