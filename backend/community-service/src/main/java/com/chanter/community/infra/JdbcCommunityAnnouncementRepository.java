package com.chanter.community.infra;

import com.chanter.community.application.CommunityAnnouncementRepository;
import com.chanter.community.domain.CommunityAnnouncement;
import com.chanter.community.domain.CommunityAnnouncementStatus;
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
public class JdbcCommunityAnnouncementRepository implements CommunityAnnouncementRepository {

    private final JdbcClient jdbcClient;
    private final DataSource dataSource;
    private volatile Boolean postgresDatabase;

    public JdbcCommunityAnnouncementRepository(JdbcClient jdbcClient, DataSource dataSource) {
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
    public CommunityAnnouncement save(CommunityAnnouncement announcement) {
        jdbcClient.sql("""
                        INSERT INTO community_announcements (
                            id, study_server_id, author_user_id, title, body, status,
                            created_at, updated_at, archived_at
                        ) VALUES (
                            :id, :studyServerId, :authorUserId, :title, :body, :status,
                            :createdAt, :updatedAt, :archivedAt
                        )
                        """)
                .param("id", announcement.id())
                .param("studyServerId", announcement.studyServerId())
                .param("authorUserId", announcement.authorUserId())
                .param("title", announcement.title())
                .param("body", announcement.body())
                .param("status", announcement.status().name())
                .param("createdAt", OffsetDateTime.ofInstant(announcement.createdAt(), ZoneOffset.UTC))
                .param("updatedAt", OffsetDateTime.ofInstant(announcement.updatedAt(), ZoneOffset.UTC))
                .param("archivedAt", announcement.archivedAt() == null
                        ? null
                        : OffsetDateTime.ofInstant(announcement.archivedAt(), ZoneOffset.UTC))
                .update();
        return findById(announcement.id(), announcement.authorUserId()).orElse(announcement);
    }

    @Override
    @Transactional
    public CommunityAnnouncement update(CommunityAnnouncement announcement) {
        jdbcClient.sql("""
                        UPDATE community_announcements
                        SET title = :title,
                            body = :body,
                            updated_at = :updatedAt
                        WHERE id = :id
                        """)
                .param("id", announcement.id())
                .param("title", announcement.title())
                .param("body", announcement.body())
                .param("updatedAt", OffsetDateTime.ofInstant(announcement.updatedAt(), ZoneOffset.UTC))
                .update();
        return findById(announcement.id(), announcement.authorUserId()).orElse(announcement);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CommunityAnnouncement> findById(UUID announcementId, UUID viewerUserId) {
        return jdbcClient.sql("""
                        SELECT a.*,
                               (SELECT COUNT(*) FROM community_announcement_reactions r
                                WHERE r.announcement_id = a.id AND r.kind = 'LIKE') AS like_count,
                               EXISTS (
                                   SELECT 1 FROM community_announcement_reactions r
                                   WHERE r.announcement_id = a.id
                                   AND r.user_id = :viewerUserId
                                   AND r.kind = 'LIKE'
                               ) AS viewer_liked
                        FROM community_announcements a
                        WHERE a.id = :announcementId
                        """)
                .param("announcementId", announcementId)
                .param("viewerUserId", viewerUserId)
                .query(this::mapAnnouncement)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommunityAnnouncement> findByStudyServer(
            UUID studyServerId,
            UUID viewerUserId,
            CommunityAnnouncementStatus status
    ) {
        return jdbcClient.sql("""
                        SELECT a.*,
                               (SELECT COUNT(*) FROM community_announcement_reactions r
                                WHERE r.announcement_id = a.id AND r.kind = 'LIKE') AS like_count,
                               EXISTS (
                                   SELECT 1 FROM community_announcement_reactions r
                                   WHERE r.announcement_id = a.id
                                   AND r.user_id = :viewerUserId
                                   AND r.kind = 'LIKE'
                               ) AS viewer_liked
                        FROM community_announcements a
                        WHERE a.study_server_id = :studyServerId
                        AND a.status = :status
                        ORDER BY a.created_at DESC, a.id DESC
                        """)
                .param("studyServerId", studyServerId)
                .param("viewerUserId", viewerUserId)
                .param("status", status.name())
                .query(this::mapAnnouncement)
                .list();
    }

    @Override
    @Transactional
    public void setStatus(
            UUID announcementId,
            CommunityAnnouncementStatus status,
            Instant updatedAt,
            Instant archivedAt
    ) {
        jdbcClient.sql("""
                        UPDATE community_announcements
                        SET status = :status,
                            updated_at = :updatedAt,
                            archived_at = :archivedAt
                        WHERE id = :id
                        """)
                .param("id", announcementId)
                .param("status", status.name())
                .param("updatedAt", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
                .param("archivedAt", archivedAt == null
                        ? null
                        : OffsetDateTime.ofInstant(archivedAt, ZoneOffset.UTC))
                .update();
    }

    @Override
    @Transactional
    public void upsertReaction(UUID announcementId, UUID userId, Instant updatedAt) {
        OffsetDateTime updated = OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC);
        if (usePostgresUpsert()) {
            jdbcClient.sql("""
                            INSERT INTO community_announcement_reactions (
                                announcement_id, user_id, kind, updated_at
                            ) VALUES (
                                :announcementId, :userId, 'LIKE', :updatedAt
                            )
                            ON CONFLICT (announcement_id, user_id, kind)
                            DO UPDATE SET updated_at = EXCLUDED.updated_at
                            """)
                    .param("announcementId", announcementId)
                    .param("userId", userId)
                    .param("updatedAt", updated)
                    .update();
            return;
        }

        Integer existing = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM community_announcement_reactions
                        WHERE announcement_id = :announcementId
                        AND user_id = :userId
                        AND kind = 'LIKE'
                        """)
                .param("announcementId", announcementId)
                .param("userId", userId)
                .query(Integer.class)
                .single();
        if (existing > 0) {
            jdbcClient.sql("""
                            UPDATE community_announcement_reactions
                            SET updated_at = :updatedAt
                            WHERE announcement_id = :announcementId
                            AND user_id = :userId
                            AND kind = 'LIKE'
                            """)
                    .param("announcementId", announcementId)
                    .param("userId", userId)
                    .param("updatedAt", updated)
                    .update();
            return;
        }
        jdbcClient.sql("""
                        INSERT INTO community_announcement_reactions (
                            announcement_id, user_id, kind, updated_at
                        ) VALUES (
                            :announcementId, :userId, 'LIKE', :updatedAt
                        )
                        """)
                .param("announcementId", announcementId)
                .param("userId", userId)
                .param("updatedAt", updated)
                .update();
    }

    @Override
    @Transactional
    public void deleteReaction(UUID announcementId, UUID userId) {
        jdbcClient.sql("""
                        DELETE FROM community_announcement_reactions
                        WHERE announcement_id = :announcementId
                        AND user_id = :userId
                        AND kind = 'LIKE'
                        """)
                .param("announcementId", announcementId)
                .param("userId", userId)
                .update();
    }

    private CommunityAnnouncement mapAnnouncement(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        OffsetDateTime archivedAt = rs.getObject("archived_at", OffsetDateTime.class);
        return new CommunityAnnouncement(
                rs.getObject("id", UUID.class),
                rs.getObject("study_server_id", UUID.class),
                rs.getObject("author_user_id", UUID.class),
                rs.getString("title"),
                rs.getString("body"),
                CommunityAnnouncementStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                archivedAt == null ? null : archivedAt.toInstant(),
                rs.getLong("like_count"),
                rs.getBoolean("viewer_liked")
        );
    }
}
