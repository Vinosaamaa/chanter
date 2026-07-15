package com.chanter.notification.infra;

import com.chanter.notification.application.NotificationRepository;
import com.chanter.notification.domain.Notification;
import com.chanter.notification.domain.NotificationFilterBucket;
import com.chanter.notification.domain.NotificationKind;
import com.chanter.notification.domain.NotificationListFilter;
import com.chanter.notification.domain.NotificationListStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcNotificationRepository implements NotificationRepository {

    private static final RowMapper<Notification> ROW_MAPPER = JdbcNotificationRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcNotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public Notification upsert(Notification notification) {
        Optional<Notification> existing = findByUniqueKey(
                notification.userId(),
                notification.sourceType(),
                notification.sourceId(),
                notification.kind().name()
        );
        if (existing.isPresent()) {
            jdbcTemplate.update("""
                    UPDATE notifications
                    SET title = ?,
                        body_preview = ?,
                        course_label = ?,
                        href = ?,
                        filter_bucket = ?,
                        study_server_id = ?,
                        course_id = ?,
                        cohort_id = ?,
                        channel_id = ?
                    WHERE id = ?
                    """,
                    notification.title(),
                    notification.bodyPreview(),
                    notification.courseLabel(),
                    notification.href(),
                    notification.filterBucket().name(),
                    notification.studyServerId(),
                    notification.courseId(),
                    notification.cohortId(),
                    notification.channelId(),
                    existing.get().id()
            );
            return findByIdForUser(existing.get().id(), existing.get().userId()).orElseThrow();
        }

        jdbcTemplate.update("""
                INSERT INTO notifications (
                    id, user_id, kind, filter_bucket, title, body_preview, course_label, href,
                    source_type, source_id, study_server_id, course_id, cohort_id, channel_id,
                    created_at, read_at, done_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                notification.id(),
                notification.userId(),
                notification.kind().name(),
                notification.filterBucket().name(),
                notification.title(),
                notification.bodyPreview(),
                notification.courseLabel(),
                notification.href(),
                notification.sourceType(),
                notification.sourceId(),
                notification.studyServerId(),
                notification.courseId(),
                notification.cohortId(),
                notification.channelId(),
                Timestamp.from(notification.createdAt()),
                notification.readAt() == null ? null : Timestamp.from(notification.readAt()),
                notification.doneAt() == null ? null : Timestamp.from(notification.doneAt())
        );
        return notification;
    }

    private Optional<Notification> findByUniqueKey(
            UUID userId,
            String sourceType,
            UUID sourceId,
            String kind
    ) {
        List<Notification> rows = jdbcTemplate.query("""
                SELECT id, user_id, kind, filter_bucket, title, body_preview, course_label, href,
                       source_type, source_id, study_server_id, course_id, cohort_id, channel_id,
                       created_at, read_at, done_at
                FROM notifications
                WHERE user_id = ? AND source_type = ? AND source_id = ? AND kind = ?
                """,
                ROW_MAPPER,
                userId,
                sourceType,
                sourceId,
                kind
        );
        return rows.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findForUser(
            UUID userId,
            NotificationListFilter filter,
            NotificationListStatus status,
            int limit
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, user_id, kind, filter_bucket, title, body_preview, course_label, href,
                       source_type, source_id, study_server_id, course_id, cohort_id, channel_id,
                       created_at, read_at, done_at
                FROM notifications
                WHERE user_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);

        if (filter == NotificationListFilter.MENTIONS) {
            sql.append(" AND filter_bucket = ?");
            args.add(NotificationFilterBucket.MENTIONS.name());
        } else if (filter == NotificationListFilter.ANNOUNCEMENTS) {
            sql.append(" AND filter_bucket = ?");
            args.add(NotificationFilterBucket.ANNOUNCEMENTS.name());
        }

        if (status == NotificationListStatus.OPEN) {
            sql.append(" AND done_at IS NULL");
        } else if (status == NotificationListStatus.DONE) {
            sql.append(" AND done_at IS NOT NULL");
        }

        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(limit);

        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM notifications
                WHERE user_id = ? AND read_at IS NULL AND done_at IS NULL
                """,
                Long.class,
                userId
        );
        return count == null ? 0L : count;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findByIdForUser(UUID notificationId, UUID userId) {
        List<Notification> rows = jdbcTemplate.query("""
                SELECT id, user_id, kind, filter_bucket, title, body_preview, course_label, href,
                       source_type, source_id, study_server_id, course_id, cohort_id, channel_id,
                       created_at, read_at, done_at
                FROM notifications
                WHERE id = ? AND user_id = ?
                """,
                ROW_MAPPER,
                notificationId,
                userId
        );
        return rows.stream().findFirst();
    }

    @Override
    @Transactional
    public boolean markRead(UUID notificationId, UUID userId, Instant readAt) {
        int updated = jdbcTemplate.update("""
                UPDATE notifications
                SET read_at = COALESCE(read_at, ?)
                WHERE id = ? AND user_id = ?
                """,
                Timestamp.from(readAt),
                notificationId,
                userId
        );
        return updated > 0;
    }

    @Override
    @Transactional
    public boolean markDone(UUID notificationId, UUID userId, Instant doneAt) {
        int updated = jdbcTemplate.update("""
                UPDATE notifications
                SET done_at = COALESCE(done_at, ?),
                    read_at = COALESCE(read_at, ?)
                WHERE id = ? AND user_id = ?
                """,
                Timestamp.from(doneAt),
                Timestamp.from(doneAt),
                notificationId,
                userId
        );
        return updated > 0;
    }

    private static Notification mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp readAt = rs.getTimestamp("read_at");
        Timestamp doneAt = rs.getTimestamp("done_at");
        return new Notification(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                NotificationKind.valueOf(rs.getString("kind")),
                NotificationFilterBucket.valueOf(rs.getString("filter_bucket")),
                rs.getString("title"),
                rs.getString("body_preview"),
                rs.getString("course_label"),
                rs.getString("href"),
                rs.getString("source_type"),
                rs.getObject("source_id", UUID.class),
                rs.getObject("study_server_id", UUID.class),
                rs.getObject("course_id", UUID.class),
                rs.getObject("cohort_id", UUID.class),
                rs.getObject("channel_id", UUID.class),
                createdAt.toInstant(),
                readAt == null ? null : readAt.toInstant(),
                doneAt == null ? null : doneAt.toInstant()
        );
    }
}
