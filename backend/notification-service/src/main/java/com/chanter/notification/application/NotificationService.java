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
    private final NotificationVisibility visibility;

    public NotificationService(NotificationRepository repository, Clock clock, NotificationVisibility visibility) {
        this.repository = repository;
        this.clock = clock;
        this.visibility = visibility;
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

    public List<Notification> list(
            UUID userId,
            NotificationListFilter filter,
            NotificationListStatus status
    ) {
        var visible = new java.util.ArrayList<Notification>();
        var access = new java.util.HashMap<SourceScope, Boolean>();
        Notification before = null;
        while (visible.size() < DEFAULT_LIST_LIMIT) {
            var page = repository.findForUser(userId, filter, status, DEFAULT_LIST_LIMIT, before, false);
            for (var notification : page) {
                if (canView(notification, access)) visible.add(notification);
                if (visible.size() == DEFAULT_LIST_LIMIT) break;
            }
            if (page.size() < DEFAULT_LIST_LIMIT) break;
            before = page.getLast();
        }
        return List.copyOf(visible);
    }

    public long unreadCount(UUID userId) {
        long count = 0;
        var access = new java.util.HashMap<SourceScope, Boolean>();
        Notification before = null;
        while (true) {
            var page = repository.findForUser(userId, NotificationListFilter.ALL, NotificationListStatus.OPEN,
                    DEFAULT_LIST_LIMIT, before, true);
            count += page.stream().filter(notification -> canView(notification, access)).count();
            if (page.size() < DEFAULT_LIST_LIMIT) return count;
            before = page.getLast();
        }
    }

    public Notification markRead(UUID notificationId, UUID userId) {
        requireVisible(notificationId, userId);
        if (!repository.markRead(notificationId, userId, clock.instant())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        return repository.findByIdForUser(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    public Notification markDone(UUID notificationId, UUID userId) {
        requireVisible(notificationId, userId);
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

    private void requireVisible(UUID id, UUID userId) {
        Notification notification = repository.findByIdForUser(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!visibility.canView(notification)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
    }

    private boolean canView(Notification notification, java.util.Map<SourceScope, Boolean> access) {
        var source = new SourceScope(notification.sourceType(), notification.sourceId(), notification.studyServerId(),
                notification.courseId(), notification.cohortId(), notification.channelId());
        return access.computeIfAbsent(source, ignored -> visibility.canView(notification));
    }
    private record SourceScope(String type, UUID id, UUID server, UUID course, UUID cohort, UUID channel) { }
}
