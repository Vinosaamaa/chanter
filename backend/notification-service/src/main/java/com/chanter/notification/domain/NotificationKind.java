package com.chanter.notification.domain;

public enum NotificationKind {
    SUPPORT_QUESTION_ANSWERED,
    SUPPORT_QUESTION_CREATED,
    OFFICE_HOURS_REMINDER,
    COMMUNITY_EVENT,
    ANNOUNCEMENT;

    public NotificationFilterBucket defaultFilterBucket() {
        return switch (this) {
            case SUPPORT_QUESTION_ANSWERED, SUPPORT_QUESTION_CREATED -> NotificationFilterBucket.MENTIONS;
            case ANNOUNCEMENT -> NotificationFilterBucket.ANNOUNCEMENTS;
            case OFFICE_HOURS_REMINDER, COMMUNITY_EVENT -> NotificationFilterBucket.OTHER;
        };
    }
}
