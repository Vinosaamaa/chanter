package com.chanter.notification.application;

import com.chanter.notification.domain.Notification;
import com.chanter.notification.domain.NotificationFilterBucket;
import com.chanter.notification.domain.NotificationKind;
import com.chanter.notification.domain.NotificationListFilter;
import com.chanter.notification.domain.NotificationListStatus;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

    private static final int DEFAULT_LIST_LIMIT = 100;

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Notification create(NotificationRepository.CreateCommand command) {
        NotificationKind kind = command.kind();
        NotificationFilterBucket filterBucket = command.filterBucket() == null
                ? kind.defaultFilterBucket()
                : command.filterBucket();

        Notification notification = new Notification(
                UUID.randomUUID(),
                command.userId(),
                kind,
                filterBucket,
                command.title().trim(),
                blankToNull(command.bodyPreview()),
                blankToNull(command.courseLabel()),
                command.href().trim(),
                command.sourceType().trim(),
                command.sourceId(),
                command.studyServerId(),
                command.courseId(),
                command.cohortId(),
                command.channelId(),
                clock.instant(),
                null,
                null
        );
        return repository.upsert(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> list(
            UUID userId,
            NotificationListFilter filter,
            NotificationListStatus status
    ) {
        return repository.findForUser(userId, filter, status, DEFAULT_LIST_LIMIT);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countUnread(userId);
    }

    @Transactional
    public Notification markRead(UUID notificationId, UUID userId) {
        if (!repository.markRead(notificationId, userId, clock.instant())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        return repository.findByIdForUser(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    @Transactional
    public Notification markDone(UUID notificationId, UUID userId) {
        if (!repository.markDone(notificationId, userId, clock.instant())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        return repository.findByIdForUser(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
