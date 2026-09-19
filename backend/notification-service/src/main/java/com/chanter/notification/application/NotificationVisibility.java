package com.chanter.notification.application;

import com.chanter.notification.domain.Notification;

public interface NotificationVisibility {
    boolean canView(Notification notification);
}
