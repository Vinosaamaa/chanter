package com.chanter.community.infra;

import com.chanter.community.application.OfficeHoursRepository;
import com.chanter.community.domain.OfficeHoursSession;
import com.chanter.community.domain.OfficeHoursSessionStatus;
import com.chanter.community.domain.OfficeHoursWaitlistEntry;
import com.chanter.community.domain.OfficeHoursWaitlistStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcOfficeHoursRepository implements OfficeHoursRepository {

    private final JdbcClient jdbcClient;

    public JdbcOfficeHoursRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public OfficeHoursSession saveSession(OfficeHoursSession session) {
        jdbcClient.sql("""
                        INSERT INTO office_hours_sessions (
                            id, cohort_id, voice_channel_id, scheduled_by_user_id,
                            starts_at, ends_at, status, created_at
                        )
                        VALUES (
                            :id, :cohortId, :voiceChannelId, :scheduledByUserId,
                            :startsAt, :endsAt, :status, :createdAt
                        )
                        """)
                .param("id", session.id())
                .param("cohortId", session.cohortId())
                .param("voiceChannelId", session.voiceChannelId())
                .param("scheduledByUserId", session.scheduledByUserId())
                .param("startsAt", OffsetDateTime.ofInstant(session.startsAt(), ZoneOffset.UTC))
                .param("endsAt", OffsetDateTime.ofInstant(session.endsAt(), ZoneOffset.UTC))
                .param("status", session.status().name())
                .param("createdAt", OffsetDateTime.ofInstant(session.createdAt(), ZoneOffset.UTC))
                .update();

        return session;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OfficeHoursSession> findSessionById(UUID sessionId) {
        return jdbcClient.sql("""
                        SELECT
                            id, cohort_id, voice_channel_id, scheduled_by_user_id,
                            starts_at, ends_at, status, created_at
                        FROM office_hours_sessions
                        WHERE id = :sessionId
                        """)
                .param("sessionId", sessionId)
                .query((rs, rowNum) -> mapSession(rs))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfficeHoursSession> findSessionsByCohortId(UUID cohortId) {
        return jdbcClient.sql("""
                        SELECT
                            id, cohort_id, voice_channel_id, scheduled_by_user_id,
                            starts_at, ends_at, status, created_at
                        FROM office_hours_sessions
                        WHERE cohort_id = :cohortId
                        ORDER BY starts_at DESC
                        """)
                .param("cohortId", cohortId)
                .query((rs, rowNum) -> mapSession(rs))
                .list();
    }

    @Override
    @Transactional
    public OfficeHoursSession updateSessionStatus(UUID sessionId, OfficeHoursSessionStatus status) {
        int updated = jdbcClient.sql("""
                        UPDATE office_hours_sessions
                        SET status = :status
                        WHERE id = :sessionId
                        """)
                .param("sessionId", sessionId)
                .param("status", status.name())
                .update();

        if (updated == 0) {
            throw new IllegalStateException("Office Hours session not found: " + sessionId);
        }

        return findSessionById(sessionId).orElseThrow();
    }

    @Override
    @Transactional
    public OfficeHoursWaitlistEntry saveWaitlistEntry(OfficeHoursWaitlistEntry entry) {
        jdbcClient.sql("""
                        INSERT INTO office_hours_waitlist_entries (
                            session_id, learner_user_id, joined_at, status
                        )
                        VALUES (:sessionId, :learnerUserId, :joinedAt, :status)
                        """)
                .param("sessionId", entry.sessionId())
                .param("learnerUserId", entry.learnerUserId())
                .param("joinedAt", OffsetDateTime.ofInstant(entry.joinedAt(), ZoneOffset.UTC))
                .param("status", entry.status().name())
                .update();

        return entry;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OfficeHoursWaitlistEntry> findWaitlistEntry(UUID sessionId, UUID learnerUserId) {
        return jdbcClient.sql("""
                        SELECT session_id, learner_user_id, joined_at, status
                        FROM office_hours_waitlist_entries
                        WHERE session_id = :sessionId
                        AND learner_user_id = :learnerUserId
                        """)
                .param("sessionId", sessionId)
                .param("learnerUserId", learnerUserId)
                .query((rs, rowNum) -> mapWaitlistEntry(rs))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfficeHoursWaitlistEntry> findWaitlistEntries(UUID sessionId) {
        return jdbcClient.sql("""
                        SELECT session_id, learner_user_id, joined_at, status
                        FROM office_hours_waitlist_entries
                        WHERE session_id = :sessionId
                        ORDER BY joined_at
                        """)
                .param("sessionId", sessionId)
                .query((rs, rowNum) -> mapWaitlistEntry(rs))
                .list();
    }

    @Override
    @Transactional
    public OfficeHoursWaitlistEntry rejoinWaitlistEntry(
            UUID sessionId,
            UUID learnerUserId,
            Instant joinedAt,
            OfficeHoursWaitlistStatus status
    ) {
        int updated = jdbcClient.sql("""
                        UPDATE office_hours_waitlist_entries
                        SET status = :status, joined_at = :joinedAt
                        WHERE session_id = :sessionId
                        AND learner_user_id = :learnerUserId
                        """)
                .param("sessionId", sessionId)
                .param("learnerUserId", learnerUserId)
                .param("joinedAt", OffsetDateTime.ofInstant(joinedAt, ZoneOffset.UTC))
                .param("status", status.name())
                .update();

        if (updated == 0) {
            throw new IllegalStateException("Waitlist entry not found");
        }

        return findWaitlistEntry(sessionId, learnerUserId).orElseThrow();
    }

    @Override
    @Transactional
    public Optional<OfficeHoursWaitlistEntry> claimNextWaitingEntry(UUID sessionId) {
        Optional<UUID> nextLearnerUserId = jdbcClient.sql("""
                        SELECT learner_user_id
                        FROM office_hours_waitlist_entries
                        WHERE session_id = :sessionId
                        AND status = :waitingStatus
                        ORDER BY joined_at
                        LIMIT 1
                        """)
                .param("sessionId", sessionId)
                .param("waitingStatus", OfficeHoursWaitlistStatus.WAITING.name())
                .query(UUID.class)
                .optional();

        if (nextLearnerUserId.isEmpty()) {
            return Optional.empty();
        }

        int updated = jdbcClient.sql("""
                        UPDATE office_hours_waitlist_entries
                        SET status = :admittedStatus
                        WHERE session_id = :sessionId
                        AND learner_user_id = :learnerUserId
                        AND status = :waitingStatus
                        """)
                .param("sessionId", sessionId)
                .param("learnerUserId", nextLearnerUserId.get())
                .param("admittedStatus", OfficeHoursWaitlistStatus.ADMITTED.name())
                .param("waitingStatus", OfficeHoursWaitlistStatus.WAITING.name())
                .update();

        if (updated == 0) {
            return Optional.empty();
        }

        return findWaitlistEntry(sessionId, nextLearnerUserId.get());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findStudyServerIdForCohort(UUID cohortId) {
        return jdbcClient.sql("""
                        SELECT co.study_server_id
                        FROM cohorts c
                        JOIN courses co ON co.id = c.course_id
                        WHERE c.id = :cohortId
                        """)
                .param("cohortId", cohortId)
                .query(UUID.class)
                .optional();
    }

    private static OfficeHoursSession mapSession(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OfficeHoursSession(
                rs.getObject("id", UUID.class),
                rs.getObject("cohort_id", UUID.class),
                rs.getObject("voice_channel_id", UUID.class),
                rs.getObject("scheduled_by_user_id", UUID.class),
                rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                rs.getObject("ends_at", OffsetDateTime.class).toInstant(),
                OfficeHoursSessionStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }

    private static OfficeHoursWaitlistEntry mapWaitlistEntry(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OfficeHoursWaitlistEntry(
                rs.getObject("session_id", UUID.class),
                rs.getObject("learner_user_id", UUID.class),
                rs.getObject("joined_at", OffsetDateTime.class).toInstant(),
                OfficeHoursWaitlistStatus.valueOf(rs.getString("status"))
        );
    }
}
