package com.chanter.media.infra;

import com.chanter.media.application.CourseResourceRepository;
import com.chanter.media.domain.CourseResource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcCourseResourceRepository implements CourseResourceRepository {

    private final JdbcClient jdbcClient;

    public JdbcCourseResourceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public CourseResource save(CourseResource courseResource) {
        OffsetDateTime createdAt = OffsetDateTime.ofInstant(courseResource.createdAt(), ZoneOffset.UTC);

        jdbcClient.sql("""
                        INSERT INTO course_resources (
                            id,
                            course_id,
                            title,
                            file_name,
                            content_type,
                            byte_size,
                            storage_key,
                            ai_approved,
                            uploaded_by_user_id,
                            created_at
                        )
                        VALUES (
                            :id,
                            :courseId,
                            :title,
                            :fileName,
                            :contentType,
                            :byteSize,
                            :storageKey,
                            :aiApproved,
                            :uploadedByUserId,
                            :createdAt
                        )
                        """)
                .param("id", courseResource.id())
                .param("courseId", courseResource.courseId())
                .param("title", courseResource.title())
                .param("fileName", courseResource.fileName())
                .param("contentType", courseResource.contentType())
                .param("byteSize", courseResource.byteSize())
                .param("storageKey", courseResource.storageKey())
                .param("aiApproved", courseResource.aiApproved())
                .param("uploadedByUserId", courseResource.uploadedByUserId())
                .param("createdAt", createdAt)
                .update();

        return courseResource;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourseResource> findById(UUID resourceId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            course_id,
                            title,
                            file_name,
                            content_type,
                            byte_size,
                            storage_key,
                            ai_approved,
                            uploaded_by_user_id,
                            created_at
                        FROM course_resources
                        WHERE id = :resourceId
                        """)
                .param("resourceId", resourceId)
                .query(this::mapCourseResource)
                .optional();
    }

    @Override
    @Transactional
    public void deleteById(UUID resourceId) {
        jdbcClient.sql("DELETE FROM course_resources WHERE id = :resourceId")
                .param("resourceId", resourceId)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseResource> findByCourseId(UUID courseId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            course_id,
                            title,
                            file_name,
                            content_type,
                            byte_size,
                            storage_key,
                            ai_approved,
                            uploaded_by_user_id,
                            created_at
                        FROM course_resources
                        WHERE course_id = :courseId
                        ORDER BY created_at ASC
                        """)
                .param("courseId", courseId)
                .query(this::mapCourseResource)
                .list();
    }

    private CourseResource mapCourseResource(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new CourseResource(
                rs.getObject("id", UUID.class),
                rs.getObject("course_id", UUID.class),
                rs.getString("title"),
                rs.getString("file_name"),
                rs.getString("content_type"),
                rs.getLong("byte_size"),
                rs.getString("storage_key"),
                rs.getBoolean("ai_approved"),
                rs.getObject("uploaded_by_user_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }
}
