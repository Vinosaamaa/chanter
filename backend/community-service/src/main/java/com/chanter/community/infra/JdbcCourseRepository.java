package com.chanter.community.infra;

import com.chanter.community.application.CourseRepository;
import com.chanter.community.domain.AccessibleStudyServer;
import com.chanter.community.domain.ChannelKind;
import com.chanter.community.domain.CohortEnrollment;
import com.chanter.community.domain.CohortEnrollmentList;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcCourseRepository implements CourseRepository {

    private final JdbcClient jdbcClient;

    public JdbcCourseRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
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
                        INSERT INTO cohorts (id, course_id, name)
                        VALUES (:id, :courseId, :name)
                        """)
                .param("id", course.cohort().id())
                .param("courseId", course.id())
                .param("name", course.cohort().name())
                .update();

        for (CourseChannel channel : course.channels()) {
            jdbcClient.sql("""
                            INSERT INTO course_channels (id, course_id, name, kind, position)
                            VALUES (:id, :courseId, :name, :kind, :position)
                            """)
                    .param("id", channel.id())
                    .param("courseId", channel.courseId())
                    .param("name", channel.name())
                    .param("kind", channel.kind().name())
                    .param("position", channel.position())
                    .update();
        }

        return course;
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
    }

    @Override
    @Transactional(readOnly = true)
    public CohortEnrollmentList listCohortEnrollments(UUID cohortId, int limit, int offset) {
        int totalCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM cohort_enrollments
                        WHERE cohort_id = :cohortId
                        """)
                .param("cohortId", cohortId)
                .query(Integer.class)
                .single();

        List<CohortEnrollment> enrollments = jdbcClient.sql("""
                        SELECT learner_user_id, enrolled_by_user_id, enrolled_at
                        FROM cohort_enrollments
                        WHERE cohort_id = :cohortId
                        ORDER BY enrolled_at DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("cohortId", cohortId)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> new CohortEnrollment(
                        rs.getObject("learner_user_id", UUID.class),
                        rs.getObject("enrolled_by_user_id", UUID.class),
                        rs.getObject("enrolled_at", OffsetDateTime.class).toInstant()
                ))
                .list();

        return new CohortEnrollmentList(enrollments, totalCount);
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
    public boolean courseChannelExists(UUID channelId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM course_channels
                        WHERE id = :channelId
                        """)
                .param("channelId", channelId)
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourseChannel> findAccessibleChannel(UUID channelId, UUID viewerUserId) {
        return jdbcClient.sql("""
                        SELECT DISTINCT cc.id, cc.course_id, cc.name, cc.kind, cc.position
                        FROM course_channels cc
                        LEFT JOIN course_roles cr ON cr.course_id = cc.course_id
                        LEFT JOIN cohorts c ON c.course_id = cc.course_id
                        LEFT JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                        WHERE cc.id = :channelId
                        AND (
                            (
                                cr.user_id = :viewerUserId
                                AND cr.role = :instructorRole
                            )
                            OR ce.learner_user_id = :viewerUserId
                        )
                        """)
                .param("channelId", channelId)
                .param("viewerUserId", viewerUserId)
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
                .query((rs, rowNum) -> new CourseChannel(
                        rs.getObject("id", UUID.class),
                        rs.getObject("course_id", UUID.class),
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
                                    FROM cohorts c
                                    JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                                    WHERE c.course_id = cc.course_id
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
                                ) THEN TRUE
                                ELSE FALSE
                            END AS can_view
                        FROM course_channels cc
                        JOIN courses co ON co.id = cc.course_id
                        WHERE cc.id = :channelId
                        AND cc.kind = :textKind
                        AND cc.name = :questionsChannelName
                        AND (
                            EXISTS (
                                SELECT 1
                                FROM cohorts c
                                JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                                WHERE c.course_id = cc.course_id
                                AND ce.learner_user_id = :userId
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM course_roles cr
                                WHERE cr.course_id = cc.course_id
                                AND cr.user_id = :userId
                                AND cr.role = :instructorRole
                            )
                        )
                        """)
                .param("channelId", channelId)
                .param("userId", userId)
                .param("textKind", ChannelKind.TEXT.name())
                .param("questionsChannelName", "questions")
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
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
                        )
                        """)
                .param("courseId", courseId)
                .param("userId", userId)
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
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
                        SELECT cc.id, cc.course_id, cc.name, cc.kind, cc.position
                        FROM course_channels cc
                        JOIN courses c ON c.id = cc.course_id
                        WHERE c.study_server_id = :studyServerId
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

        if (!canViewAllGrants && enrolledCourseIds.isEmpty()) {
            return Optional.empty();
        }

        List<UUID> accessibleCourseChannelIds = enrolledCourseIds.isEmpty()
                ? List.of()
                : jdbcClient.sql("""
                                SELECT cc.id
                                FROM course_channels cc
                                WHERE cc.course_id IN (
                                    SELECT co.id
                                    FROM courses co
                                    JOIN cohorts c ON c.course_id = co.id
                                    JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                                    WHERE co.study_server_id = :studyServerId
                                    AND ce.learner_user_id = :userId
                                )
                                ORDER BY cc.position
                                """)
                        .param("studyServerId", studyServerId)
                        .param("userId", userId)
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
                        )
                        """)
                .param("cohortId", cohortId)
                .param("userId", userId)
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
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
                        )
                        """)
                .param("cohortId", cohortId)
                .param("userId", userId)
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
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
                        ORDER BY ss.name
                        """)
                .param("userId", userId)
                .param("ownerRole", StudyServerRole.STUDY_SERVER_OWNER.name())
                .param("instructorRole", CourseRole.INSTRUCTOR.name())
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
