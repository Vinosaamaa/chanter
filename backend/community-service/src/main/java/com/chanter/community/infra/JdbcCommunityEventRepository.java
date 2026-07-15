package com.chanter.community.infra;

import com.chanter.community.application.CommunityEventRepository;
import com.chanter.community.domain.CommunityEvent;
import com.chanter.community.domain.CommunityEventFilter;
import com.chanter.community.domain.CommunityEventRsvpStatus;
import com.chanter.community.domain.CommunityEventStatus;
import com.chanter.community.domain.CommunityEventVisibility;
import com.chanter.community.domain.CourseRole;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcCommunityEventRepository implements CommunityEventRepository {

    private final JdbcClient jdbcClient;
    private final DataSource dataSource;
    private volatile Boolean postgresDatabase;

    public JdbcCommunityEventRepository(JdbcClient jdbcClient, DataSource dataSource) {
        this.jdbcClient = jdbcClient;
        this.dataSource = dataSource;
    }

    private boolean usePostgresUpsert() {
        if (postgresDatabase == null) {
            synchronized (this) {
                if (postgresDatabase == null) {
                    postgresDatabase = isPostgresDatabase(dataSource);
                }
            }
        }
        return postgresDatabase;
    }

    private static boolean isPostgresDatabase(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to detect database product", ex);
        }
    }

    @Override
    @Transactional
    public CommunityEvent save(CommunityEvent event) {
        jdbcClient.sql("""
                        INSERT INTO community_events (
                            id, study_server_id, title, description, location,
                            starts_at, ends_at, capacity, visibility, course_id, cohort_id,
                            created_by_user_id, status, created_at, updated_at
                        ) VALUES (
                            :id, :studyServerId, :title, :description, :location,
                            :startsAt, :endsAt, :capacity, :visibility, :courseId, :cohortId,
                            :createdByUserId, :status, :createdAt, :updatedAt
                        )
                        """)
                .param("id", event.id())
                .param("studyServerId", event.studyServerId())
                .param("title", event.title())
                .param("description", event.description())
                .param("location", event.location())
                .param("startsAt", OffsetDateTime.ofInstant(event.startsAt(), ZoneOffset.UTC))
                .param("endsAt", OffsetDateTime.ofInstant(event.endsAt(), ZoneOffset.UTC))
                .param("capacity", event.capacity())
                .param("visibility", event.visibility().name())
                .param("courseId", event.courseId())
                .param("cohortId", event.cohortId())
                .param("createdByUserId", event.createdByUserId())
                .param("status", event.status().name())
                .param("createdAt", OffsetDateTime.ofInstant(event.createdAt(), ZoneOffset.UTC))
                .param("updatedAt", OffsetDateTime.ofInstant(event.updatedAt(), ZoneOffset.UTC))
                .update();
        return findById(event.id(), event.createdByUserId()).orElse(event);
    }

    @Override
    @Transactional
    public CommunityEvent update(CommunityEvent event) {
        jdbcClient.sql("""
                        UPDATE community_events
                        SET title = :title,
                            description = :description,
                            location = :location,
                            starts_at = :startsAt,
                            ends_at = :endsAt,
                            capacity = :capacity,
                            visibility = :visibility,
                            course_id = :courseId,
                            cohort_id = :cohortId,
                            updated_at = :updatedAt
                        WHERE id = :id
                        """)
                .param("id", event.id())
                .param("title", event.title())
                .param("description", event.description())
                .param("location", event.location())
                .param("startsAt", OffsetDateTime.ofInstant(event.startsAt(), ZoneOffset.UTC))
                .param("endsAt", OffsetDateTime.ofInstant(event.endsAt(), ZoneOffset.UTC))
                .param("capacity", event.capacity())
                .param("visibility", event.visibility().name())
                .param("courseId", event.courseId())
                .param("cohortId", event.cohortId())
                .param("updatedAt", OffsetDateTime.ofInstant(event.updatedAt(), ZoneOffset.UTC))
                .update();
        return findById(event.id(), event.createdByUserId()).orElse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CommunityEvent> findById(UUID eventId, UUID viewerUserId) {
        return jdbcClient.sql("""
                        SELECT e.*,
                               (SELECT COUNT(*) FROM community_event_rsvps r
                                WHERE r.event_id = e.id AND r.status = 'GOING') AS going_count,
                               (SELECT COUNT(*) FROM community_event_rsvps r
                                WHERE r.event_id = e.id AND r.status = 'INTERESTED') AS interested_count,
                               (SELECT r.status FROM community_event_rsvps r
                                WHERE r.event_id = e.id AND r.user_id = :viewerUserId) AS viewer_rsvp
                        FROM community_events e
                        WHERE e.id = :eventId
                        """)
                .param("eventId", eventId)
                .param("viewerUserId", viewerUserId)
                .query(this::mapEvent)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommunityEvent> findVisibleEvents(
            UUID studyServerId,
            UUID viewerUserId,
            CommunityEventFilter filter,
            Instant now
    ) {
        String timeClause = switch (filter) {
            case UPCOMING -> " AND e.ends_at >= :now AND e.status = 'SCHEDULED'";
            case PAST -> " AND e.ends_at < :now";
            case GOING -> " AND EXISTS (SELECT 1 FROM community_event_rsvps r"
                    + " WHERE r.event_id = e.id AND r.user_id = :viewerUserId AND r.status = 'GOING')";
        };
        String orderClause = filter == CommunityEventFilter.PAST
                ? " ORDER BY e.starts_at DESC"
                : " ORDER BY e.starts_at ASC";

        return jdbcClient.sql("""
                        SELECT e.*,
                               (SELECT COUNT(*) FROM community_event_rsvps r
                                WHERE r.event_id = e.id AND r.status = 'GOING') AS going_count,
                               (SELECT COUNT(*) FROM community_event_rsvps r
                                WHERE r.event_id = e.id AND r.status = 'INTERESTED') AS interested_count,
                               (SELECT r.status FROM community_event_rsvps r
                                WHERE r.event_id = e.id AND r.user_id = :viewerUserId) AS viewer_rsvp
                        FROM community_events e
                        WHERE e.study_server_id = :studyServerId
                        AND (
                            e.visibility = 'HUB'
                            OR (
                                e.visibility = 'COURSE'
                                AND e.course_id IS NOT NULL
                                AND (
                                    EXISTS (
                                        SELECT 1 FROM course_roles cr
                                        WHERE cr.course_id = e.course_id
                                        AND cr.user_id = :viewerUserId
                                        AND cr.role = :instructorRole
                                    )
                                    OR EXISTS (
                                        SELECT 1 FROM cohorts c
                                        JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                                        WHERE c.course_id = e.course_id
                                        AND ce.learner_user_id = :viewerUserId
                                    )
                                )
                            )
                            OR (
                                e.visibility = 'COHORT'
                                AND e.cohort_id IS NOT NULL
                                AND (
                                    EXISTS (
                                        SELECT 1 FROM cohort_enrollments ce
                                        WHERE ce.cohort_id = e.cohort_id
                                        AND ce.learner_user_id = :viewerUserId
                                    )
                                    OR EXISTS (
                                        SELECT 1 FROM course_roles cr
                                        WHERE cr.course_id = e.course_id
                                        AND cr.user_id = :viewerUserId
                                        AND cr.role = :instructorRole
                                    )
                                )
                            )
                        )
                        """ + timeClause + orderClause)
                .param("studyServerId", studyServerId)
                .param("viewerUserId", viewerUserId)
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .query(this::mapEvent)
                .list();
    }

    @Override
    @Transactional
    public void setStatus(UUID eventId, CommunityEventStatus status, Instant updatedAt) {
        jdbcClient.sql("""
                        UPDATE community_events
                        SET status = :status,
                            updated_at = :updatedAt
                        WHERE id = :eventId
                        """)
                .param("eventId", eventId)
                .param("status", status.name())
                .param("updatedAt", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
                .update();
    }

    @Override
    @Transactional
    public void upsertRsvp(UUID eventId, UUID userId, CommunityEventRsvpStatus status, Instant updatedAt) {
        if (usePostgresUpsert()) {
            jdbcClient.sql("""
                            INSERT INTO community_event_rsvps (event_id, user_id, status, updated_at)
                            VALUES (:eventId, :userId, :status, :updatedAt)
                            ON CONFLICT (event_id, user_id)
                            DO UPDATE SET status = EXCLUDED.status, updated_at = EXCLUDED.updated_at
                            """)
                    .param("eventId", eventId)
                    .param("userId", userId)
                    .param("status", status.name())
                    .param("updatedAt", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
                    .update();
            return;
        }

        int updated = jdbcClient.sql("""
                        UPDATE community_event_rsvps
                        SET status = :status,
                            updated_at = :updatedAt
                        WHERE event_id = :eventId
                        AND user_id = :userId
                        """)
                .param("eventId", eventId)
                .param("userId", userId)
                .param("status", status.name())
                .param("updatedAt", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
                .update();
        if (updated == 0) {
            jdbcClient.sql("""
                            INSERT INTO community_event_rsvps (event_id, user_id, status, updated_at)
                            VALUES (:eventId, :userId, :status, :updatedAt)
                            """)
                    .param("eventId", eventId)
                    .param("userId", userId)
                    .param("status", status.name())
                    .param("updatedAt", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
                    .update();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCourseAccessible(UUID courseId, UUID userId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM courses co
                        WHERE co.id = :courseId
                        AND (
                            EXISTS (
                                SELECT 1 FROM course_roles cr
                                WHERE cr.course_id = co.id
                                AND cr.user_id = :userId
                                AND cr.role = :instructorRole
                            )
                            OR EXISTS (
                                SELECT 1 FROM cohorts c
                                JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                                WHERE c.course_id = co.id
                                AND ce.learner_user_id = :userId
                            )
                            OR EXISTS (
                                SELECT 1 FROM study_servers ss
                                WHERE ss.id = co.study_server_id
                                AND ss.owner_user_id = :userId
                            )
                        )
                        """)
                .param("courseId", courseId)
                .param("userId", userId)
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCohortAccessible(UUID cohortId, UUID userId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM cohorts c
                        JOIN courses co ON co.id = c.course_id
                        WHERE c.id = :cohortId
                        AND (
                            EXISTS (
                                SELECT 1 FROM cohort_enrollments ce
                                WHERE ce.cohort_id = c.id
                                AND ce.learner_user_id = :userId
                            )
                            OR EXISTS (
                                SELECT 1 FROM course_roles cr
                                WHERE cr.course_id = co.id
                                AND cr.user_id = :userId
                                AND cr.role = :instructorRole
                            )
                            OR EXISTS (
                                SELECT 1 FROM study_servers ss
                                WHERE ss.id = co.study_server_id
                                AND ss.owner_user_id = :userId
                            )
                        )
                        """)
                .param("cohortId", cohortId)
                .param("userId", userId)
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean courseBelongsToStudyServer(UUID courseId, UUID studyServerId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM courses
                        WHERE id = :courseId
                        AND study_server_id = :studyServerId
                        """)
                .param("courseId", courseId)
                .param("studyServerId", studyServerId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean cohortBelongsToCourse(UUID cohortId, UUID courseId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM cohorts
                        WHERE id = :cohortId
                        AND course_id = :courseId
                        """)
                .param("cohortId", cohortId)
                .param("courseId", courseId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    private CommunityEvent mapEvent(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String viewerRsvp = rs.getString("viewer_rsvp");
        return new CommunityEvent(
                rs.getObject("id", UUID.class),
                rs.getObject("study_server_id", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("location"),
                rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                rs.getObject("ends_at", OffsetDateTime.class).toInstant(),
                (Integer) rs.getObject("capacity"),
                CommunityEventVisibility.valueOf(rs.getString("visibility")),
                rs.getObject("course_id", UUID.class),
                rs.getObject("cohort_id", UUID.class),
                rs.getObject("created_by_user_id", UUID.class),
                CommunityEventStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                rs.getLong("going_count"),
                rs.getLong("interested_count"),
                viewerRsvp == null ? null : CommunityEventRsvpStatus.valueOf(viewerRsvp)
        );
    }
}
