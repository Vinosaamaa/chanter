package com.chanter.community.infra;

import com.chanter.community.application.OfficeHoursRepository;
import com.chanter.community.domain.OfficeHoursSession;
import com.chanter.community.domain.OfficeHoursParticipant;
import com.chanter.community.domain.OfficeHoursSessionStatus;
import com.chanter.community.domain.OfficeHoursWaitlistEntry;
import com.chanter.community.domain.OfficeHoursWaitlistStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    public OfficeHoursSession updateSessionSchedule(UUID sessionId, Instant startsAt, Instant endsAt) {
        int updated = jdbcClient.sql("""
                        UPDATE office_hours_sessions
                        SET starts_at = :startsAt, ends_at = :endsAt
                        WHERE id = :sessionId
                        """)
                .param("sessionId", sessionId)
                .param("startsAt", OffsetDateTime.ofInstant(startsAt, ZoneOffset.UTC))
                .param("endsAt", OffsetDateTime.ofInstant(endsAt, ZoneOffset.UTC))
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

    @Override
    @Transactional
    public OfficeHoursParticipant saveParticipant(OfficeHoursParticipant participant) {
        int updated = jdbcClient.sql("""
                        UPDATE office_hours_participants
                        SET can_speak = :canSpeak,
                            hand_raised = :handRaised,
                            active = TRUE,
                            joined_at = :joinedAt,
                            updated_at = :updatedAt
                        WHERE session_id = :sessionId
                        AND user_id = :userId
                        """)
                .param("sessionId", participant.sessionId())
                .param("userId", participant.userId())
                .param("canSpeak", participant.canSpeak())
                .param("handRaised", participant.handRaised())
                .param("joinedAt", OffsetDateTime.ofInstant(participant.joinedAt(), ZoneOffset.UTC))
                .param("updatedAt", OffsetDateTime.ofInstant(participant.updatedAt(), ZoneOffset.UTC))
                .update();
        if (updated == 0) {
            jdbcClient.sql("""
                            INSERT INTO office_hours_participants (
                                session_id, user_id, can_speak, hand_raised, active, joined_at, updated_at
                            )
                            VALUES (
                                :sessionId, :userId, :canSpeak, :handRaised, TRUE, :joinedAt, :updatedAt
                            )
                            """)
                    .param("sessionId", participant.sessionId())
                    .param("userId", participant.userId())
                    .param("canSpeak", participant.canSpeak())
                    .param("handRaised", participant.handRaised())
                    .param("joinedAt", OffsetDateTime.ofInstant(participant.joinedAt(), ZoneOffset.UTC))
                    .param("updatedAt", OffsetDateTime.ofInstant(participant.updatedAt(), ZoneOffset.UTC))
                    .update();
        }
        return participant;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OfficeHoursParticipant> findParticipant(UUID sessionId, UUID userId) {
        return jdbcClient.sql("""
                        SELECT session_id, user_id, can_speak, hand_raised, active, joined_at, updated_at
                        FROM office_hours_participants
                        WHERE session_id = :sessionId
                        AND user_id = :userId
                        """)
                .param("sessionId", sessionId)
                .param("userId", userId)
                .query((rs, rowNum) -> mapParticipant(rs))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfficeHoursParticipant> findActiveParticipants(UUID sessionId) {
        return jdbcClient.sql("""
                        SELECT session_id, user_id, can_speak, hand_raised, active, joined_at, updated_at
                        FROM office_hours_participants
                        WHERE session_id = :sessionId
                        AND active = TRUE
                        ORDER BY can_speak DESC, hand_raised DESC, joined_at, user_id
                        """)
                .param("sessionId", sessionId)
                .query((rs, rowNum) -> mapParticipant(rs))
                .list();
    }

    @Override
    @Transactional
    public OfficeHoursParticipant updateParticipantHand(
            UUID sessionId,
            UUID userId,
            boolean raised,
            Instant updatedAt
    ) {
        int updated = jdbcClient.sql("""
                        UPDATE office_hours_participants
                        SET hand_raised = :raised, updated_at = :updatedAt
                        WHERE session_id = :sessionId
                        AND user_id = :userId
                        AND active = TRUE
                        """)
                .param("sessionId", sessionId)
                .param("userId", userId)
                .param("raised", raised)
                .param("updatedAt", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
                .update();
        if (updated == 0) {
            throw new IllegalStateException("Active Office Hours participant not found");
        }
        return findParticipant(sessionId, userId).orElseThrow();
    }

    @Override
    @Transactional
    public OfficeHoursParticipant updateParticipantSpeaking(
            UUID sessionId,
            UUID userId,
            boolean canSpeak,
            Instant updatedAt
    ) {
        int updated = jdbcClient.sql("""
                        UPDATE office_hours_participants
                        SET can_speak = :canSpeak,
                            hand_raised = FALSE,
                            updated_at = :updatedAt
                        WHERE session_id = :sessionId
                        AND user_id = :userId
                        AND active = TRUE
                        """)
                .param("sessionId", sessionId)
                .param("userId", userId)
                .param("canSpeak", canSpeak)
                .param("updatedAt", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
                .update();
        if (updated == 0) {
            throw new IllegalStateException("Active Office Hours participant not found");
        }
        return findParticipant(sessionId, userId).orElseThrow();
    }

    @Override
    @Transactional
    public void deactivateParticipant(UUID sessionId, UUID userId, Instant updatedAt) {
        jdbcClient.sql("""
                        UPDATE office_hours_participants
                        SET active = FALSE,
                            can_speak = FALSE,
                            hand_raised = FALSE,
                            updated_at = :updatedAt
                        WHERE session_id = :sessionId
                        AND user_id = :userId
                        """)
                .param("sessionId", sessionId)
                .param("userId", userId)
                .param("updatedAt", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
                .update();
    }

    @Override
    @Transactional
    public void deactivateParticipants(UUID sessionId, Instant updatedAt) {
        jdbcClient.sql("""
                        UPDATE office_hours_participants
                        SET active = FALSE,
                            can_speak = FALSE,
                            hand_raised = FALSE,
                            updated_at = :updatedAt
                        WHERE session_id = :sessionId
                        AND active = TRUE
                        """)
                .param("sessionId", sessionId)
                .param("updatedAt", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
                .update();
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

    private static OfficeHoursParticipant mapParticipant(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OfficeHoursParticipant(
                rs.getObject("session_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getBoolean("can_speak"),
                rs.getBoolean("hand_raised"),
                rs.getBoolean("active"),
                rs.getObject("joined_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }
}
