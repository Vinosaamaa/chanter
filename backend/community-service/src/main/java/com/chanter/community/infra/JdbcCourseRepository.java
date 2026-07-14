package com.chanter.community.infra;

import com.chanter.community.application.CourseRepository;
import com.chanter.community.domain.AccessibleStudyServer;
import com.chanter.community.domain.ChannelKind;
import com.chanter.community.domain.CohortEnrollment;
import com.chanter.community.domain.CohortEnrollmentList;
import com.chanter.community.domain.CohortInvitation;
import com.chanter.community.domain.CohortInvitationStatus;
import com.chanter.community.domain.CohortRole;
import com.chanter.community.domain.Course;
import com.chanter.community.domain.CourseChannel;
import com.chanter.community.domain.CourseRole;
import com.chanter.community.domain.CohortTaQueueAccess;
import com.chanter.community.domain.CohortOfficeHoursAccess;
import com.chanter.community.domain.CourseResourceAccess;
import com.chanter.community.domain.GrantCandidateCohort;
import com.chanter.community.domain.GrantCandidateCourse;
import com.chanter.community.domain.StudyAssistantGrantCandidates;
import com.chanter.community.domain.StudyAssistantViewerScope;
import com.chanter.community.domain.StudyServerChannel;
import com.chanter.community.domain.StudyServerRole;
import com.chanter.community.domain.SupportQuestionChannelAccess;
import com.chanter.community.domain.VoicePresence;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcCourseRepository implements CourseRepository {

    private static final String COHORT_ENROLLMENT_FROM_WHERE = """
            FROM cohort_enrollments
            WHERE cohort_id = :cohortId
            """;

    private final JdbcClient jdbcClient;
    private final DataSource dataSource;
    private volatile Boolean postgresDatabase;

    public JdbcCourseRepository(JdbcClient jdbcClient, DataSource dataSource) {
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
    public Course save(Course course) {
        jdbcClient.sql("""
                        INSERT INTO courses (id, study_server_id, title, instructor_user_id, created_at)
                        VALUES (:id, :studyServerId, :title, :instructorUserId, :createdAt)
                        """)
                .param("id", course.id())
                .param("studyServerId", course.studyServerId())
                .param("title", course.title())
                .param("instructorUserId", course.instructorRole().userId())
                .param("createdAt", OffsetDateTime.ofInstant(course.createdAt(), ZoneOffset.UTC))
                .update();

        jdbcClient.sql("""
                        INSERT INTO course_roles (course_id, user_id, role)
                        VALUES (:courseId, :userId, :role)
                        """)
                .param("courseId", course.id())
                .param("userId", course.instructorRole().userId())
                .param("role", course.instructorRole().role().name())
                .update();

        jdbcClient.sql("""
                        INSERT INTO cohorts (id, course_id, name, invite_code)
                        VALUES (:id, :courseId, :name, :inviteCode)
                        """)
                .param("id", course.cohort().id())
                .param("courseId", course.id())
                .param("name", course.cohort().name())
                .param("inviteCode", course.cohort().inviteCode())
                .update();

        for (CourseChannel channel : course.channels()) {
            jdbcClient.sql("""
                            INSERT INTO course_channels (id, course_id, cohort_id, name, kind, position)
                            VALUES (:id, :courseId, :cohortId, :name, :kind, :position)
                            """)
                    .param("id", channel.id())
                    .param("courseId", channel.courseId())
                    .param("cohortId", channel.cohortId())
                    .param("name", channel.name())
                    .param("kind", channel.kind().name())
                    .param("position", channel.position())
                    .update();
        }

        return course;
    }

    @Override
    @Transactional
    public CourseChannel saveChannel(CourseChannel channel) {
        jdbcClient.sql("""
                        INSERT INTO course_channels (id, course_id, cohort_id, name, kind, position)
                        VALUES (:id, :courseId, :cohortId, :name, :kind, :position)
                        """)
                .param("id", channel.id())
                .param("courseId", channel.courseId())
                .param("cohortId", channel.cohortId())
                .param("name", channel.name())
                .param("kind", channel.kind().name())
                .param("position", channel.position())
                .update();
        return channel;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourseChannel> findActiveChannelById(UUID channelId) {
        return jdbcClient.sql("""
                        SELECT id, course_id, cohort_id, name, kind, position
                        FROM course_channels
                        WHERE id = :channelId
                        AND archived_at IS NULL
                        """)
                .param("channelId", channelId)
                .query((rs, rowNum) -> new CourseChannel(
                        rs.getObject("id", UUID.class),
                        rs.getObject("course_id", UUID.class),
                        rs.getObject("cohort_id", UUID.class),
                        rs.getString("name"),
                        ChannelKind.valueOf(rs.getString("kind")),
                        rs.getInt("position")
                ))
                .optional();
    }

    @Override
    @Transactional
    public void renameChannel(UUID channelId, String name) {
        jdbcClient.sql("""
                        UPDATE course_channels
                        SET name = :name
                        WHERE id = :channelId
                        AND archived_at IS NULL
                        """)
                .param("channelId", channelId)
                .param("name", name)
                .update();
    }

    @Override
    @Transactional
    public void archiveChannel(UUID channelId, Instant archivedAt) {
        jdbcClient.sql("""
                        UPDATE course_channels
                        SET archived_at = :archivedAt
                        WHERE id = :channelId
                        AND archived_at IS NULL
                        """)
                .param("channelId", channelId)
                .param("archivedAt", OffsetDateTime.ofInstant(archivedAt, ZoneOffset.UTC))
                .update();
    }

    @Override
    @Transactional
    public void lockCohortForChannelMutation(UUID cohortId) {
        jdbcClient.sql("SELECT id FROM cohorts WHERE id = :cohortId FOR UPDATE")
                .param("cohortId", cohortId)
                .query(UUID.class)
                .single();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean activeChannelNameExists(UUID cohortId, String name) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM course_channels
                        WHERE cohort_id = :cohortId
                        AND name = :name
                        AND archived_at IS NULL
                        """)
                .param("cohortId", cohortId)
                .param("name", name)
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean activeChannelNameExistsExcluding(UUID cohortId, String name, UUID excludedChannelId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM course_channels
                        WHERE cohort_id = :cohortId
                        AND name = :name
                        AND id <> :excludedChannelId
                        AND archived_at IS NULL
                        """)
                .param("cohortId", cohortId)
                .param("name", name)
                .param("excludedChannelId", excludedChannelId)
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    @Transactional
    public VoicePresence saveCourseVoicePresence(
            UUID channelId,
            UUID memberUserId,
            Instant joinedAt,
            Instant expiresAt
    ) {
        OffsetDateTime joinedAtUtc = OffsetDateTime.ofInstant(joinedAt, ZoneOffset.UTC);
        OffsetDateTime expiresAtUtc = OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC);
        if (usePostgresUpsert()) {
            jdbcClient.sql("""
                            INSERT INTO course_voice_channel_presences (
                                channel_id,
                                member_user_id,
                                joined_at,
                                expires_at
                            )
                            VALUES (:channelId, :memberUserId, :joinedAt, :expiresAt)
                            ON CONFLICT (channel_id, member_user_id)
                            DO UPDATE SET joined_at = EXCLUDED.joined_at,
                                          expires_at = EXCLUDED.expires_at
                            """)
                    .param("channelId", channelId)
                    .param("memberUserId", memberUserId)
                    .param("joinedAt", joinedAtUtc)
                    .param("expiresAt", expiresAtUtc)
                    .update();
        } else {
            jdbcClient.sql("""
                            MERGE INTO course_voice_channel_presences (
                                channel_id,
                                member_user_id,
                                joined_at,
                                expires_at
                            )
                            KEY (channel_id, member_user_id)
                            VALUES (:channelId, :memberUserId, :joinedAt, :expiresAt)
                            """)
                    .param("channelId", channelId)
                    .param("memberUserId", memberUserId)
                    .param("joinedAt", joinedAtUtc)
                    .param("expiresAt", expiresAtUtc)
                    .update();
        }
        return new VoicePresence(channelId, memberUserId, true, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoicePresence> findCourseVoicePresences(UUID channelId, Instant activeAt) {
        return jdbcClient.sql("""
                        SELECT channel_id, member_user_id
                        FROM course_voice_channel_presences
                        WHERE channel_id = :channelId
                        AND expires_at > :activeAt
                        ORDER BY joined_at, member_user_id
                        """)
                .param("channelId", channelId)
                .param("activeAt", OffsetDateTime.ofInstant(activeAt, ZoneOffset.UTC))
                .query((rs, rowNum) -> new VoicePresence(
                        rs.getObject("channel_id", UUID.class),
                        rs.getObject("member_user_id", UUID.class),
                        true,
                        true
                ))
                .list();
    }

    @Override
    @Transactional
    public void deleteCourseVoicePresence(UUID channelId, UUID memberUserId) {
        jdbcClient.sql("""
                        DELETE FROM course_voice_channel_presences
                        WHERE channel_id = :channelId
                        AND member_user_id = :memberUserId
                        """)
                .param("channelId", channelId)
                .param("memberUserId", memberUserId)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findCourseIdByCohortId(UUID cohortId) {
        return jdbcClient.sql("SELECT course_id FROM cohorts WHERE id = :cohortId")
                .param("cohortId", cohortId)
                .query(UUID.class)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public int findNextChannelPosition(UUID cohortId) {
        return jdbcClient.sql("""
                        SELECT COALESCE(MAX(position), -1) + 1
                        FROM course_channels
                        WHERE cohort_id = :cohortId
                        """)
                .param("cohortId", cohortId)
                .query(Integer.class)
                .single();
    }

    @Override
    @Transactional
    public void enrollLearner(UUID cohortId, UUID learnerUserId, UUID enrolledByUserId, Instant enrolledAt) {
        try {
            jdbcClient.sql("""
                            INSERT INTO cohort_enrollments (
                                cohort_id,
                                learner_user_id,
                                enrolled_by_user_id,
                                enrolled_at
                            )
                            VALUES (:cohortId, :learnerUserId, :enrolledByUserId, :enrolledAt)
                            """)
                    .param("cohortId", cohortId)
                    .param("learnerUserId", learnerUserId)
                    .param("enrolledByUserId", enrolledByUserId)
                    .param("enrolledAt", OffsetDateTime.ofInstant(enrolledAt, ZoneOffset.UTC))
                    .update();
        } catch (DuplicateKeyException ignored) {
            // Re-enrolling the same learner is idempotent for this vertical slice.
        }
        jdbcClient.sql("""
                        UPDATE cohort_invitations
                        SET status = :status,
                            resolved_at = :resolvedAt
                        WHERE cohort_id = :cohortId
                        AND invited_user_id = :learnerUserId
                        AND status = :pendingStatus
                        """)
                .param("status", CohortInvitationStatus.ACCEPTED.name())
                .param("resolvedAt", OffsetDateTime.ofInstant(enrolledAt, ZoneOffset.UTC))
                .param("cohortId", cohortId)
                .param("learnerUserId", learnerUserId)
                .param("pendingStatus", CohortInvitationStatus.PENDING.name())
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public CohortEnrollmentList listCohortEnrollments(
            UUID cohortId,
            int limit,
            int offset,
            String learnerSearch
    ) {
        boolean hasLearnerSearch = learnerSearch != null;
        String searchPattern = hasLearnerSearch ? "%" + learnerSearch + "%" : null;
        String searchFilter = hasLearnerSearch
                ? " AND LOWER(CAST(learner_user_id AS TEXT)) LIKE :searchPattern\n"
                : "";
        String enrollmentOrderAndPage = """
                        ORDER BY enrolled_at DESC, learner_user_id ASC
                        LIMIT :limit OFFSET :offset
                        """;

        var countQuery = jdbcClient.sql("""
                        SELECT COUNT(*)
                        """ + COHORT_ENROLLMENT_FROM_WHERE + searchFilter)
                .param("cohortId", cohortId);
        if (hasLearnerSearch) {
            countQuery = countQuery.param("searchPattern", searchPattern);
        }
        int totalCount = countQuery.query(Integer.class).single();

        var enrollmentQuery = jdbcClient.sql("""
                        SELECT learner_user_id, enrolled_by_user_id, enrolled_at, assigned_ta_user_id
                        """ + COHORT_ENROLLMENT_FROM_WHERE + searchFilter + enrollmentOrderAndPage)
                .param("cohortId", cohortId)
                .param("limit", limit)
                .param("offset", offset);
        if (hasLearnerSearch) {
            enrollmentQuery = enrollmentQuery.param("searchPattern", searchPattern);
        }
        List<CohortEnrollment> enrollments = enrollmentQuery.query(this::mapCohortEnrollment).list();

        return new CohortEnrollmentList(enrollments, totalCount, limit, offset);
    }

    private CohortEnrollment mapCohortEnrollment(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new CohortEnrollment(
                rs.getObject("learner_user_id", UUID.class),
                rs.getObject("enrolled_by_user_id", UUID.class),
                rs.getObject("enrolled_at", OffsetDateTime.class).toInstant(),
                rs.getObject("assigned_ta_user_id", UUID.class)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findCohortInviteCode(UUID cohortId) {
        return jdbcClient.sql("""
                        SELECT invite_code
                        FROM cohorts
                        WHERE id = :cohortId
                        """)
                .param("cohortId", cohortId)
                .query(UUID.class)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findCohortInviteCodeForInstructor(UUID cohortId, UUID instructorUserId) {
        return jdbcClient.sql("""
                        SELECT c.invite_code
                        FROM cohorts c
                        JOIN course_roles cr ON cr.course_id = c.course_id
                        WHERE c.id = :cohortId
                        AND cr.user_id = :instructorUserId
                        AND cr.role = :role
                        """)
                .param("cohortId", cohortId)
                .param("instructorUserId", instructorUserId)
                .param("role", CourseRole.INSTRUCTOR.name())
                .query(UUID.class)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean cohortExists(UUID cohortId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM cohorts
                        WHERE id = :cohortId
                        """)
                .param("cohortId", cohortId)
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean cohortHasInstructor(UUID cohortId, UUID instructorUserId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM cohorts c
                        JOIN course_roles cr ON cr.course_id = c.course_id
                        WHERE c.id = :cohortId
                        AND cr.user_id = :instructorUserId
                        AND cr.role = :role
                        """)
                .param("cohortId", cohortId)
                .param("instructorUserId", instructorUserId)
                .param("role", CourseRole.INSTRUCTOR.name())
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean cohortHasRosterViewer(UUID cohortId, UUID viewerUserId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM cohorts c
                        JOIN courses co ON co.id = c.course_id
                        WHERE c.id = :cohortId
                        AND (
                            EXISTS (
                                SELECT 1
                                FROM course_roles cr
                                WHERE cr.course_id = c.course_id
                                AND cr.user_id = :viewerUserId
                                AND cr.role = :instructorRole
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM cohort_enrollments ce
                                WHERE ce.cohort_id = c.id
                                AND ce.learner_user_id = :viewerUserId
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM cohort_roles cor
                                WHERE cor.cohort_id = c.id
                                AND cor.user_id = :viewerUserId
                                AND cor.role = :teachingAssistantRole
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM study_server_roles ssr
                                WHERE ssr.study_server_id = co.study_server_id
                                AND ssr.user_id = :viewerUserId
                                AND ssr.role = :ownerRole
                            )
                        )
                        """)
                .param("cohortId", cohortId)
                .param("viewerUserId", viewerUserId)
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
                .param("teachingAssistantRole", CohortRole.TA.name())
                .param("ownerRole", StudyServerRole.STUDY_SERVER_OWNER.name())
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean cohortHasPeopleManager(UUID cohortId, UUID viewerUserId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM cohorts c
                        JOIN courses co ON co.id = c.course_id
                        WHERE c.id = :cohortId
                        AND (
                            EXISTS (
                                SELECT 1
                                FROM course_roles cr
                                WHERE cr.course_id = c.course_id
                                AND cr.user_id = :viewerUserId
                                AND cr.role = :instructorRole
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM study_server_roles ssr
                                WHERE ssr.study_server_id = co.study_server_id
                                AND ssr.user_id = :viewerUserId
                                AND ssr.role = :ownerRole
                            )
                        )
                        """)
                .param("cohortId", cohortId)
                .param("viewerUserId", viewerUserId)
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
                .param("ownerRole", StudyServerRole.STUDY_SERVER_OWNER.name())
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findCohortInstructorUserId(UUID cohortId) {
        return jdbcClient.sql("""
                        SELECT cr.user_id
                        FROM cohorts c
                        JOIN course_roles cr ON cr.course_id = c.course_id
                        WHERE c.id = :cohortId
                        AND cr.role = :role
                        """)
                .param("cohortId", cohortId)
                .param("role", CourseRole.INSTRUCTOR.name())
                .query(UUID.class)
                .optional();
    }

    @Override
    @Transactional
    public void addTeachingAssistant(UUID cohortId, UUID userId) {
        try {
            jdbcClient.sql("""
                            INSERT INTO cohort_roles (cohort_id, user_id, role)
                            VALUES (:cohortId, :userId, :role)
                            """)
                    .param("cohortId", cohortId)
                    .param("userId", userId)
                    .param("role", CohortRole.TA.name())
                    .update();
        } catch (DuplicateKeyException ignored) {
            // Adding the same TA twice is idempotent.
        }
    }

    @Override
    @Transactional
    public void removeTeachingAssistant(UUID cohortId, UUID userId) {
        jdbcClient.sql("""
                        UPDATE cohort_enrollments
                        SET assigned_ta_user_id = NULL
                        WHERE cohort_id = :cohortId
                        AND assigned_ta_user_id = :userId
                        """)
                .param("cohortId", cohortId)
                .param("userId", userId)
                .update();
        jdbcClient.sql("""
                        DELETE FROM cohort_roles
                        WHERE cohort_id = :cohortId
                        AND user_id = :userId
                        AND role = :role
                        """)
                .param("cohortId", cohortId)
                .param("userId", userId)
                .param("role", CohortRole.TA.name())
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findTeachingAssistantUserIds(UUID cohortId) {
        return jdbcClient.sql("""
                        SELECT user_id
                        FROM cohort_roles
                        WHERE cohort_id = :cohortId
                        AND role = :role
                        ORDER BY user_id
                        """)
                .param("cohortId", cohortId)
                .param("role", CohortRole.TA.name())
                .query(UUID.class)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean cohortHasTeachingAssistant(UUID cohortId, UUID userId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM cohort_roles
                        WHERE cohort_id = :cohortId
                        AND user_id = :userId
                        AND role = :role
                        """)
                .param("cohortId", cohortId)
                .param("userId", userId)
                .param("role", CohortRole.TA.name())
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean cohortHasEnrollments(UUID cohortId, List<UUID> learnerUserIds) {
        if (learnerUserIds.isEmpty()) {
            return false;
        }
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM cohort_enrollments
                        WHERE cohort_id = :cohortId
                        AND learner_user_id IN (:learnerUserIds)
                        """)
                .param("cohortId", cohortId)
                .param("learnerUserIds", learnerUserIds)
                .query(Integer.class)
                .single() == learnerUserIds.size();
    }

    @Override
    @Transactional
    public void assignTeachingAssistant(
            UUID cohortId,
            List<UUID> learnerUserIds,
            UUID teachingAssistantUserId
    ) {
        jdbcClient.sql("""
                        UPDATE cohort_enrollments
                        SET assigned_ta_user_id = :teachingAssistantUserId
                        WHERE cohort_id = :cohortId
                        AND learner_user_id IN (:learnerUserIds)
                        """)
                .param("teachingAssistantUserId", teachingAssistantUserId)
                .param("cohortId", cohortId)
                .param("learnerUserIds", learnerUserIds)
                .update();
    }

    @Override
    @Transactional
    public void removeEnrollment(UUID cohortId, UUID learnerUserId) {
        jdbcClient.sql("""
                        UPDATE cohort_enrollments
                        SET assigned_ta_user_id = NULL
                        WHERE cohort_id = :cohortId
                        AND assigned_ta_user_id = :learnerUserId
                        """)
                .param("cohortId", cohortId)
                .param("learnerUserId", learnerUserId)
                .update();
        jdbcClient.sql("""
                        DELETE FROM cohort_roles
                        WHERE cohort_id = :cohortId
                        AND user_id = :learnerUserId
                        """)
                .param("cohortId", cohortId)
                .param("learnerUserId", learnerUserId)
                .update();
        jdbcClient.sql("""
                        DELETE FROM cohort_enrollments
                        WHERE cohort_id = :cohortId
                        AND learner_user_id = :learnerUserId
                        """)
                .param("cohortId", cohortId)
                .param("learnerUserId", learnerUserId)
                .update();
    }

    @Override
    @Transactional
    public CohortInvitation saveInvitation(CohortInvitation invitation) {
        int updated = jdbcClient.sql("""
                        UPDATE cohort_invitations
                        SET id = :id,
                            email = :email,
                            invited_by_user_id = :invitedByUserId,
                            status = :status,
                            created_at = :createdAt,
                            resolved_at = NULL
                        WHERE cohort_id = :cohortId
                        AND invited_user_id = :invitedUserId
                        """)
                .param("id", invitation.id())
                .param("email", invitation.email())
                .param("invitedByUserId", invitation.invitedByUserId())
                .param("status", invitation.status().name())
                .param("createdAt", OffsetDateTime.ofInstant(invitation.createdAt(), ZoneOffset.UTC))
                .param("cohortId", invitation.cohortId())
                .param("invitedUserId", invitation.invitedUserId())
                .update();
        if (updated == 0) {
            jdbcClient.sql("""
                            INSERT INTO cohort_invitations (
                                id,
                                cohort_id,
                                invited_user_id,
                                email,
                                invited_by_user_id,
                                status,
                                created_at
                            )
                            VALUES (
                                :id,
                                :cohortId,
                                :invitedUserId,
                                :email,
                                :invitedByUserId,
                                :status,
                                :createdAt
                            )
                            """)
                    .param("id", invitation.id())
                    .param("cohortId", invitation.cohortId())
                    .param("invitedUserId", invitation.invitedUserId())
                    .param("email", invitation.email())
                    .param("invitedByUserId", invitation.invitedByUserId())
                    .param("status", invitation.status().name())
                    .param("createdAt", OffsetDateTime.ofInstant(invitation.createdAt(), ZoneOffset.UTC))
                    .update();
        }
        return invitation;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CohortInvitation> findPendingInvitations(UUID cohortId) {
        return jdbcClient.sql("""
                        SELECT id,
                               cohort_id,
                               invited_user_id,
                               email,
                               invited_by_user_id,
                               status,
                               created_at
                        FROM cohort_invitations
                        WHERE cohort_id = :cohortId
                        AND status = :status
                        ORDER BY created_at DESC, id ASC
                        """)
                .param("cohortId", cohortId)
                .param("status", CohortInvitationStatus.PENDING.name())
                .query((rs, rowNum) -> new CohortInvitation(
                        rs.getObject("id", UUID.class),
                        rs.getObject("cohort_id", UUID.class),
                        rs.getObject("invited_user_id", UUID.class),
                        rs.getString("email"),
                        rs.getObject("invited_by_user_id", UUID.class),
                        CohortInvitationStatus.valueOf(rs.getString("status")),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()
                ))
                .list();
    }

    @Override
    @Transactional
    public void cancelInvitation(UUID cohortId, UUID invitationId, Instant resolvedAt) {
        jdbcClient.sql("""
                        UPDATE cohort_invitations
                        SET status = :status,
                            resolved_at = :resolvedAt
                        WHERE cohort_id = :cohortId
                        AND id = :invitationId
                        AND status = :pendingStatus
                        """)
                .param("status", CohortInvitationStatus.CANCELLED.name())
                .param("resolvedAt", OffsetDateTime.ofInstant(resolvedAt, ZoneOffset.UTC))
                .param("cohortId", cohortId)
                .param("invitationId", invitationId)
                .param("pendingStatus", CohortInvitationStatus.PENDING.name())
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean courseChannelExists(UUID channelId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM course_channels
                        WHERE id = :channelId
                        AND archived_at IS NULL
                        """)
                .param("channelId", channelId)
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourseChannel> findAccessibleChannel(UUID channelId, UUID viewerUserId) {
        return jdbcClient.sql("""
                        SELECT DISTINCT cc.id, cc.course_id, cc.cohort_id, cc.name, cc.kind, cc.position
                        FROM course_channels cc
                        LEFT JOIN course_roles cr ON cr.course_id = cc.course_id
                        LEFT JOIN cohorts c ON c.id = cc.cohort_id
                        LEFT JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                        WHERE cc.id = :channelId
                        AND cc.archived_at IS NULL
                        AND (
                            (
                                cr.user_id = :viewerUserId
                                AND cr.role = :instructorRole
                            )
                            OR ce.learner_user_id = :viewerUserId
                            OR EXISTS (
                                SELECT 1
                                FROM cohort_roles cor
                                WHERE cor.cohort_id = cc.cohort_id
                                AND cor.user_id = :viewerUserId
                                AND cor.role = :teachingAssistantRole
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM courses co
                                JOIN study_server_roles ssr ON ssr.study_server_id = co.study_server_id
                                WHERE co.id = cc.course_id
                                AND ssr.user_id = :viewerUserId
                                AND ssr.role = :studyServerOwnerRole
                            )
                        )
                        """)
                .param("channelId", channelId)
                .param("viewerUserId", viewerUserId)
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
                .param("teachingAssistantRole", CohortRole.TA.name())
                .param("studyServerOwnerRole", StudyServerRole.STUDY_SERVER_OWNER.name())
                .query((rs, rowNum) -> new CourseChannel(
                        rs.getObject("id", UUID.class),
                        rs.getObject("course_id", UUID.class),
                        rs.getObject("cohort_id", UUID.class),
                        rs.getString("name"),
                        ChannelKind.valueOf(rs.getString("kind")),
                        rs.getInt("position")
                ))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupportQuestionChannelAccess> findSupportQuestionChannelAccess(UUID channelId, UUID userId) {
        return jdbcClient.sql("""
                        SELECT
                            cc.id,
                            cc.course_id,
                            co.study_server_id,
                            cc.name,
                            CASE
                                WHEN EXISTS (
                                    SELECT 1
                                    FROM cohort_enrollments ce
                                    WHERE ce.cohort_id = cc.cohort_id
                                    AND ce.learner_user_id = :userId
                                ) THEN TRUE
                                ELSE FALSE
                            END AS can_post,
                            CASE
                                WHEN EXISTS (
                                    SELECT 1
                                    FROM course_roles cr
                                    WHERE cr.course_id = cc.course_id
                                    AND cr.user_id = :userId
                                    AND cr.role = :instructorRole
                                )
                                OR EXISTS (
                                    SELECT 1
                                    FROM cohort_roles cor
                                    WHERE cor.cohort_id = cc.cohort_id
                                    AND cor.user_id = :userId
                                    AND cor.role = :teachingAssistantRole
                                ) THEN TRUE
                                ELSE FALSE
                            END AS can_view
                        FROM course_channels cc
                        JOIN courses co ON co.id = cc.course_id
                        WHERE cc.id = :channelId
                        AND cc.archived_at IS NULL
                        AND cc.kind = :textKind
                        AND cc.name = :questionsChannelName
                        AND (
                            EXISTS (
                                SELECT 1
                                FROM cohort_enrollments ce
                                WHERE ce.cohort_id = cc.cohort_id
                                AND ce.learner_user_id = :userId
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM course_roles cr
                                WHERE cr.course_id = cc.course_id
                                AND cr.user_id = :userId
                                AND cr.role = :instructorRole
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM cohort_roles cor
                                WHERE cor.cohort_id = cc.cohort_id
                                AND cor.user_id = :userId
                                AND cor.role = :teachingAssistantRole
                            )
                        )
                        """)
                .param("channelId", channelId)
                .param("userId", userId)
                .param("textKind", ChannelKind.TEXT.name())
                .param("questionsChannelName", "questions")
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
                .param("teachingAssistantRole", CohortRole.TA.name())
                .query((rs, rowNum) -> new SupportQuestionChannelAccess(
                        rs.getObject("id", UUID.class),
                        rs.getObject("course_id", UUID.class),
                        rs.getObject("study_server_id", UUID.class),
                        rs.getString("name"),
                        rs.getBoolean("can_post"),
                        rs.getBoolean("can_view")
                ))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean courseExists(UUID courseId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM courses
                        WHERE id = :courseId
                        """)
                .param("courseId", courseId)
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourseResourceAccess> findCourseResourceAccess(UUID courseId, UUID userId) {
        return jdbcClient.sql("""
                        SELECT
                            c.id AS course_id,
                            CASE
                                WHEN EXISTS (
                                    SELECT 1
                                    FROM course_roles cr
                                    WHERE cr.course_id = c.id
                                    AND cr.user_id = :userId
                                    AND cr.role = :instructorRole
                                ) THEN TRUE
                                ELSE FALSE
                            END AS can_upload,
                            CASE
                                WHEN EXISTS (
                                    SELECT 1
                                    FROM course_roles cr
                                    WHERE cr.course_id = c.id
                                    AND cr.user_id = :userId
                                    AND cr.role = :instructorRole
                                )
                                OR EXISTS (
                                    SELECT 1
                                    FROM cohorts co
                                    JOIN cohort_enrollments ce ON ce.cohort_id = co.id
                                    WHERE co.course_id = c.id
                                    AND ce.learner_user_id = :userId
                                )
                                OR EXISTS (
                                    SELECT 1
                                    FROM cohorts co
                                    JOIN cohort_roles cor ON cor.cohort_id = co.id
                                    WHERE co.course_id = c.id
                                    AND cor.user_id = :userId
                                    AND cor.role = :teachingAssistantRole
                                ) THEN TRUE
                                ELSE FALSE
                            END AS can_view
                        FROM courses c
                        WHERE c.id = :courseId
                        AND (
                            EXISTS (
                                SELECT 1
                                FROM course_roles cr
                                WHERE cr.course_id = c.id
                                AND cr.user_id = :userId
                                AND cr.role = :instructorRole
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM cohorts co
                                JOIN cohort_enrollments ce ON ce.cohort_id = co.id
                                WHERE co.course_id = c.id
                                AND ce.learner_user_id = :userId
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM cohorts co
                                JOIN cohort_roles cor ON cor.cohort_id = co.id
                                WHERE co.course_id = c.id
                                AND cor.user_id = :userId
                                AND cor.role = :teachingAssistantRole
                            )
                        )
                        """)
                .param("courseId", courseId)
                .param("userId", userId)
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
                .param("teachingAssistantRole", CohortRole.TA.name())
                .query((rs, rowNum) -> new CourseResourceAccess(
                        rs.getObject("course_id", UUID.class),
                        rs.getBoolean("can_upload"),
                        rs.getBoolean("can_view")
                ))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isStudyServerOwner(UUID studyServerId, UUID userId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM study_server_roles
                        WHERE study_server_id = :studyServerId
                        AND user_id = :userId
                        AND role = :ownerRole
                        """)
                .param("studyServerId", studyServerId)
                .param("userId", userId)
                .param("ownerRole", StudyServerRole.STUDY_SERVER_OWNER.name())
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isInstructorOnAnyCourseInStudyServer(UUID studyServerId, UUID userId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM courses c
                        JOIN course_roles cr ON cr.course_id = c.id
                        WHERE c.study_server_id = :studyServerId
                        AND cr.user_id = :userId
                        AND cr.role = :instructorRole
                        """)
                .param("studyServerId", studyServerId)
                .param("userId", userId)
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findInstructedCourseIds(UUID studyServerId, UUID userId) {
        return jdbcClient.sql("""
                        SELECT c.id
                        FROM courses c
                        JOIN course_roles cr ON cr.course_id = c.id
                        WHERE c.study_server_id = :studyServerId
                        AND cr.user_id = :userId
                        AND cr.role = :instructorRole
                        ORDER BY c.id
                        """)
                .param("studyServerId", studyServerId)
                .param("userId", userId)
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
                .query(UUID.class)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findTeachingAssistantCohortIds(UUID studyServerId, UUID userId) {
        return jdbcClient.sql("""
                        SELECT c.id
                        FROM cohorts c
                        JOIN courses co ON co.id = c.course_id
                        JOIN cohort_roles cor ON cor.cohort_id = c.id
                        WHERE co.study_server_id = :studyServerId
                        AND cor.user_id = :userId
                        AND cor.role = :teachingAssistantRole
                        ORDER BY c.id
                        """)
                .param("studyServerId", studyServerId)
                .param("userId", userId)
                .param("teachingAssistantRole", CohortRole.TA.name())
                .query(UUID.class)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean studyServerExists(UUID studyServerId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM study_servers
                        WHERE id = :studyServerId
                        """)
                .param("studyServerId", studyServerId)
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudyAssistantGrantCandidates> findGrantCandidates(UUID studyServerId) {
        if (!studyServerExists(studyServerId)) {
            return Optional.empty();
        }

        List<StudyServerChannel> studyServerChannels = jdbcClient.sql("""
                        SELECT id, study_server_id, name, kind, position
                        FROM study_server_channels
                        WHERE study_server_id = :studyServerId
                        ORDER BY position
                        """)
                .param("studyServerId", studyServerId)
                .query((rs, rowNum) -> new StudyServerChannel(
                        rs.getObject("id", UUID.class),
                        rs.getObject("study_server_id", UUID.class),
                        rs.getString("name"),
                        ChannelKind.valueOf(rs.getString("kind")),
                        rs.getInt("position")
                ))
                .list();

        List<CourseRow> courseRows = jdbcClient.sql("""
                        SELECT id, title
                        FROM courses
                        WHERE study_server_id = :studyServerId
                        ORDER BY title
                        """)
                .param("studyServerId", studyServerId)
                .query((rs, rowNum) -> new CourseRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("title")
                ))
                .list();

        if (courseRows.isEmpty()) {
            return Optional.of(new StudyAssistantGrantCandidates(studyServerId, studyServerChannels, List.of()));
        }

        Map<UUID, List<GrantCandidateCohort>> cohortsByCourse = new HashMap<>();
        jdbcClient.sql("""
                        SELECT co.id, co.name, co.course_id
                        FROM cohorts co
                        JOIN courses c ON c.id = co.course_id
                        WHERE c.study_server_id = :studyServerId
                        ORDER BY co.course_id, co.name
                        """)
                .param("studyServerId", studyServerId)
                .query((rs, rowNum) -> {
                    UUID courseId = rs.getObject("course_id", UUID.class);
                    cohortsByCourse
                            .computeIfAbsent(courseId, ignored -> new ArrayList<>())
                            .add(new GrantCandidateCohort(
                                    rs.getObject("id", UUID.class),
                                    rs.getString("name")
                            ));
                    return null;
                })
                .list();

        Map<UUID, List<CourseChannel>> channelsByCourse = new HashMap<>();
        jdbcClient.sql("""
                        SELECT cc.id, cc.course_id, cc.cohort_id, cc.name, cc.kind, cc.position
                        FROM course_channels cc
                        JOIN courses c ON c.id = cc.course_id
                        WHERE c.study_server_id = :studyServerId
                        AND cc.archived_at IS NULL
                        ORDER BY cc.course_id, cc.position
                        """)
                .param("studyServerId", studyServerId)
                .query((rs, rowNum) -> {
                    UUID courseId = rs.getObject("course_id", UUID.class);
                    channelsByCourse
                            .computeIfAbsent(courseId, ignored -> new ArrayList<>())
                            .add(new CourseChannel(
                                    rs.getObject("id", UUID.class),
                                    courseId,
                                    rs.getObject("cohort_id", UUID.class),
                                    rs.getString("name"),
                                    ChannelKind.valueOf(rs.getString("kind")),
                                    rs.getInt("position")
                            ));
                    return null;
                })
                .list();

        List<GrantCandidateCourse> courses = courseRows.stream()
                .map(course -> new GrantCandidateCourse(
                        course.id(),
                        course.title(),
                        cohortsByCourse.getOrDefault(course.id(), List.of()),
                        channelsByCourse.getOrDefault(course.id(), List.of())
                ))
                .toList();

        return Optional.of(new StudyAssistantGrantCandidates(studyServerId, studyServerChannels, courses));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudyAssistantViewerScope> findViewerScope(UUID studyServerId, UUID userId) {
        if (!studyServerExists(studyServerId)) {
            return Optional.empty();
        }

        boolean canViewAllGrants = isStudyServerOwner(studyServerId, userId)
                || isInstructorOnAnyCourseInStudyServer(studyServerId, userId);

        List<UUID> enrolledCohortIds = jdbcClient.sql("""
                        SELECT c.id
                        FROM cohorts c
                        JOIN courses co ON co.id = c.course_id
                        JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                        WHERE co.study_server_id = :studyServerId
                        AND ce.learner_user_id = :userId
                        ORDER BY c.name
                        """)
                .param("studyServerId", studyServerId)
                .param("userId", userId)
                .query(UUID.class)
                .list();

        List<UUID> enrolledCourseIds = jdbcClient.sql("""
                        SELECT DISTINCT co.id
                        FROM courses co
                        JOIN cohorts c ON c.course_id = co.id
                        JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                        WHERE co.study_server_id = :studyServerId
                        AND ce.learner_user_id = :userId
                        ORDER BY co.id
                        """)
                .param("studyServerId", studyServerId)
                .param("userId", userId)
                .query(UUID.class)
                .list();

        List<UUID> teachingAssistantCohortIds = findTeachingAssistantCohortIds(studyServerId, userId);

        if (!canViewAllGrants && enrolledCourseIds.isEmpty() && teachingAssistantCohortIds.isEmpty()) {
            return Optional.empty();
        }

        List<UUID> accessibleCourseChannelIds = enrolledCourseIds.isEmpty()
                && teachingAssistantCohortIds.isEmpty()
                ? List.of()
                : jdbcClient.sql("""
                                SELECT cc.id
                                FROM course_channels cc
                                JOIN courses co ON co.id = cc.course_id
                                WHERE cc.archived_at IS NULL
                                AND co.study_server_id = :studyServerId
                                AND (
                                    EXISTS (
                                        SELECT 1
                                        FROM cohort_enrollments ce
                                        WHERE ce.cohort_id = cc.cohort_id
                                    AND ce.learner_user_id = :userId
                                    )
                                    OR EXISTS (
                                        SELECT 1
                                        FROM cohort_roles cor
                                        WHERE cor.cohort_id = cc.cohort_id
                                        AND cor.user_id = :userId
                                        AND cor.role = :teachingAssistantRole
                                    )
                                )
                                ORDER BY cc.position
                                """)
                        .param("studyServerId", studyServerId)
                        .param("userId", userId)
                        .param("teachingAssistantRole", CohortRole.TA.name())
                        .query(UUID.class)
                        .list();

        return Optional.of(new StudyAssistantViewerScope(
                studyServerId,
                canViewAllGrants,
                enrolledCourseIds,
                enrolledCohortIds,
                accessibleCourseChannelIds
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CohortTaQueueAccess> findCohortTaQueueAccess(UUID cohortId, UUID userId) {
        return jdbcClient.sql("""
                        SELECT
                            c.id AS cohort_id,
                            c.course_id,
                            co.study_server_id,
                            CASE
                                WHEN EXISTS (
                                    SELECT 1
                                    FROM cohort_enrollments ce
                                    WHERE ce.cohort_id = c.id
                                    AND ce.learner_user_id = :userId
                                ) THEN TRUE
                                ELSE FALSE
                            END AS can_add,
                            CASE
                                WHEN EXISTS (
                                    SELECT 1
                                    FROM course_roles cr
                                    WHERE cr.course_id = c.course_id
                                    AND cr.user_id = :userId
                                    AND cr.role = :instructorRole
                                )
                                OR EXISTS (
                                    SELECT 1
                                    FROM cohort_roles cor
                                    WHERE cor.cohort_id = c.id
                                    AND cor.user_id = :userId
                                    AND cor.role = :teachingAssistantRole
                                ) THEN TRUE
                                ELSE FALSE
                            END AS can_manage
                        FROM cohorts c
                        JOIN courses co ON co.id = c.course_id
                        WHERE c.id = :cohortId
                        AND (
                            EXISTS (
                                SELECT 1
                                FROM cohort_enrollments ce
                                WHERE ce.cohort_id = c.id
                                AND ce.learner_user_id = :userId
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM course_roles cr
                                WHERE cr.course_id = c.course_id
                                AND cr.user_id = :userId
                                AND cr.role = :instructorRole
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM cohort_roles cor
                                WHERE cor.cohort_id = c.id
                                AND cor.user_id = :userId
                                AND cor.role = :teachingAssistantRole
                            )
                        )
                        """)
                .param("cohortId", cohortId)
                .param("userId", userId)
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
                .param("teachingAssistantRole", CohortRole.TA.name())
                .query((rs, rowNum) -> new CohortTaQueueAccess(
                        rs.getObject("cohort_id", UUID.class),
                        rs.getObject("course_id", UUID.class),
                        rs.getObject("study_server_id", UUID.class),
                        rs.getBoolean("can_add"),
                        rs.getBoolean("can_manage")
                ))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CohortOfficeHoursAccess> findCohortOfficeHoursAccess(UUID cohortId, UUID userId) {
        return jdbcClient.sql("""
                        SELECT
                            c.id AS cohort_id,
                            c.course_id,
                            co.study_server_id,
                            CASE
                                WHEN EXISTS (
                                    SELECT 1
                                    FROM course_roles cr
                                    WHERE cr.course_id = c.course_id
                                    AND cr.user_id = :userId
                                    AND cr.role = :instructorRole
                                ) THEN TRUE
                                ELSE FALSE
                            END AS can_schedule,
                            CASE
                                WHEN EXISTS (
                                    SELECT 1
                                    FROM cohort_enrollments ce
                                    WHERE ce.cohort_id = c.id
                                    AND ce.learner_user_id = :userId
                                )
                                OR EXISTS (
                                    SELECT 1
                                    FROM cohort_roles cor
                                    WHERE cor.cohort_id = c.id
                                    AND cor.user_id = :userId
                                    AND cor.role = :teachingAssistantRole
                                ) THEN TRUE
                                ELSE FALSE
                            END AS can_join,
                            CASE
                                WHEN EXISTS (
                                    SELECT 1
                                    FROM course_roles cr
                                    WHERE cr.course_id = c.course_id
                                    AND cr.user_id = :userId
                                    AND cr.role = :instructorRole
                                ) THEN TRUE
                                ELSE FALSE
                            END AS can_manage
                        FROM cohorts c
                        JOIN courses co ON co.id = c.course_id
                        WHERE c.id = :cohortId
                        AND (
                            EXISTS (
                                SELECT 1
                                FROM cohort_enrollments ce
                                WHERE ce.cohort_id = c.id
                                AND ce.learner_user_id = :userId
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM course_roles cr
                                WHERE cr.course_id = c.course_id
                                AND cr.user_id = :userId
                                AND cr.role = :instructorRole
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM cohort_roles cor
                                WHERE cor.cohort_id = c.id
                                AND cor.user_id = :userId
                                AND cor.role = :teachingAssistantRole
                            )
                        )
                        """)
                .param("cohortId", cohortId)
                .param("userId", userId)
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
                .param("teachingAssistantRole", CohortRole.TA.name())
                .query((rs, rowNum) -> new CohortOfficeHoursAccess(
                        rs.getObject("cohort_id", UUID.class),
                        rs.getObject("course_id", UUID.class),
                        rs.getObject("study_server_id", UUID.class),
                        rs.getBoolean("can_schedule"),
                        rs.getBoolean("can_join"),
                        rs.getBoolean("can_manage")
                ))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccessibleStudyServer> listAccessibleStudyServers(UUID userId) {
        List<AccessibleStudyServerRow> rows = jdbcClient.sql("""
                        SELECT ss.id,
                               ss.name,
                               EXISTS (
                                   SELECT 1
                                   FROM study_server_roles ssr
                                   WHERE ssr.study_server_id = ss.id
                                   AND ssr.user_id = :userId
                                   AND ssr.role = :ownerRole
                               ) AS is_owner,
                               (
                                   SELECT COUNT(*)
                                   FROM courses co
                                   WHERE co.study_server_id = ss.id
                               ) AS course_count
                        FROM study_servers ss
                        WHERE EXISTS (
                            SELECT 1
                            FROM study_server_roles ssr
                            WHERE ssr.study_server_id = ss.id
                            AND ssr.user_id = :userId
                            AND ssr.role = :ownerRole
                        )
                        OR EXISTS (
                            SELECT 1
                            FROM courses co
                            JOIN course_roles cr ON cr.course_id = co.id
                            WHERE co.study_server_id = ss.id
                            AND cr.user_id = :userId
                            AND cr.role = :instructorRole
                        )
                        OR EXISTS (
                            SELECT 1
                            FROM courses co
                            JOIN cohorts c ON c.course_id = co.id
                            JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                            WHERE co.study_server_id = ss.id
                            AND ce.learner_user_id = :userId
                        )
                        OR EXISTS (
                            SELECT 1
                            FROM courses co
                            JOIN cohorts c ON c.course_id = co.id
                            JOIN cohort_roles cor ON cor.cohort_id = c.id
                            WHERE co.study_server_id = ss.id
                            AND cor.user_id = :userId
                            AND cor.role = :teachingAssistantRole
                        )
                        ORDER BY ss.name
                        """)
                .param("userId", userId)
                .param("ownerRole", StudyServerRole.STUDY_SERVER_OWNER.name())
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
                .param("teachingAssistantRole", CohortRole.TA.name())
                .query((rs, rowNum) -> new AccessibleStudyServerRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getBoolean("is_owner"),
                        rs.getInt("course_count")
                ))
                .list();

        if (rows.isEmpty()) {
            return List.of();
        }

        Map<UUID, Integer> memberCounts = memberCountsForStudyServers(
                rows.stream().map(AccessibleStudyServerRow::id).toList()
        );

        return rows.stream()
                .map(row -> new AccessibleStudyServer(
                        row.id(),
                        row.name(),
                        row.owner(),
                        row.courseCount(),
                        memberCounts.getOrDefault(row.id(), 0)
                ))
                .toList();
    }

    private Map<UUID, Integer> memberCountsForStudyServers(List<UUID> studyServerIds) {
        Map<UUID, Integer> memberCounts = new java.util.HashMap<>();
        jdbcClient.sql("""
                        SELECT study_server_id, COUNT(DISTINCT member_user_id) AS member_count
                        FROM (
                            SELECT ssr.study_server_id, ssr.user_id AS member_user_id
                            FROM study_server_roles ssr
                            WHERE ssr.study_server_id IN (:studyServerIds)
                            UNION
                            SELECT co.study_server_id, cr.user_id AS member_user_id
                            FROM courses co
                            JOIN course_roles cr ON cr.course_id = co.id
                            WHERE co.study_server_id IN (:studyServerIds)
                            UNION
                            SELECT co.study_server_id, ce.learner_user_id AS member_user_id
                            FROM courses co
                            JOIN cohorts c ON c.course_id = co.id
                            JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                            WHERE co.study_server_id IN (:studyServerIds)
                            UNION
                            SELECT co.study_server_id, cor.user_id AS member_user_id
                            FROM courses co
                            JOIN cohorts c ON c.course_id = co.id
                            JOIN cohort_roles cor ON cor.cohort_id = c.id
                            WHERE co.study_server_id IN (:studyServerIds)
                        ) members
                        GROUP BY study_server_id
                        """)
                .param("studyServerIds", studyServerIds)
                .query((rs, rowNum) -> {
                    memberCounts.put(
                            rs.getObject("study_server_id", UUID.class),
                            rs.getInt("member_count")
                    );
                    return null;
                })
                .list();
        return memberCounts;
    }

    private record AccessibleStudyServerRow(
            UUID id,
            String name,
            boolean owner,
            int courseCount
    ) {
    }

    private record CourseRow(UUID id, String title) {
    }
}
