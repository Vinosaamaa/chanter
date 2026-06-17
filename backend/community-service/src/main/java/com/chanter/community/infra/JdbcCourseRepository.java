package com.chanter.community.infra;

import com.chanter.community.application.CourseRepository;
import com.chanter.community.domain.ChannelKind;
import com.chanter.community.domain.Course;
import com.chanter.community.domain.CourseChannel;
import com.chanter.community.domain.CourseRole;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
}
