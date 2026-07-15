package com.chanter.community.infra;

import com.chanter.community.application.StudyServerRepository;
import com.chanter.community.domain.ChannelKind;
import com.chanter.community.domain.CoMember;
import com.chanter.community.domain.OwnerRole;
import com.chanter.community.domain.SaasPlanTier;
import com.chanter.community.domain.StudyServer;
import com.chanter.community.domain.StudyServerChannel;
import com.chanter.community.domain.StudyServerInvitation;
import com.chanter.community.domain.StudyServerInvitationStatus;
import com.chanter.community.domain.StudyServerMember;
import com.chanter.community.domain.StudyServerRole;
import com.chanter.community.domain.StudyServerType;
import com.chanter.community.domain.VoicePresence;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcStudyServerRepository implements StudyServerRepository {

    private final JdbcClient jdbcClient;
    private final DataSource dataSource;
    private volatile Boolean postgresDatabase;

    public JdbcStudyServerRepository(JdbcClient jdbcClient, DataSource dataSource) {
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
    public StudyServer save(StudyServer studyServer) {
        jdbcClient.sql("""
                        INSERT INTO study_servers (
                            id,
                            name,
                            description,
                            server_type,
                            owner_user_id,
                            plan_tier,
                            created_at
                        )
                        VALUES (
                            :id,
                            :name,
                            :description,
                            :serverType,
                            :ownerUserId,
                            :planTier,
                            :createdAt
                        )
                        """)
                .param("id", studyServer.id())
                .param("name", studyServer.name())
                .param("description", studyServer.description())
                .param("serverType", studyServer.serverType() == null ? null : studyServer.serverType().name())
                .param("ownerUserId", studyServer.ownerRole().userId())
                .param("planTier", studyServer.planTier().name())
                .param("createdAt", OffsetDateTime.ofInstant(studyServer.createdAt(), ZoneOffset.UTC))
                .update();

        jdbcClient.sql("""
                        INSERT INTO study_server_roles (study_server_id, user_id, role)
                        VALUES (:studyServerId, :userId, :role)
                        """)
                .param("studyServerId", studyServer.id())
                .param("userId", studyServer.ownerRole().userId())
                .param("role", studyServer.ownerRole().role().name())
                .update();

        for (StudyServerChannel channel : studyServer.channels()) {
            jdbcClient.sql("""
                            INSERT INTO study_server_channels (id, study_server_id, name, kind, position)
                            VALUES (:id, :studyServerId, :name, :kind, :position)
                            """)
                    .param("id", channel.id())
                    .param("studyServerId", studyServer.id())
                    .param("name", channel.name())
                    .param("kind", channel.kind().name())
                    .param("position", channel.position())
                    .update();
        }

        return studyServer;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudyServer> findById(UUID id) {
        return jdbcClient.sql("""
                        SELECT id, name, description, server_type, owner_user_id, plan_tier, created_at
                        FROM study_servers
                        WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> new StudyServer(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("description"),
                        mapServerType(rs.getString("server_type")),
                        ownerRoleFor(id),
                        SaasPlanTier.valueOf(rs.getString("plan_tier")),
                        channelsFor(id),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()
                ))
                .optional();
    }

    private static StudyServerType mapServerType(String serverType) {
        if (serverType == null) {
            return null;
        }
        return StudyServerType.valueOf(serverType);
    }

    private OwnerRole ownerRoleFor(UUID studyServerId) {
        return jdbcClient.sql("""
                        SELECT user_id, role
                        FROM study_server_roles
                        WHERE study_server_id = :studyServerId
                        AND role = :role
                        """)
                .param("studyServerId", studyServerId)
                .param("role", StudyServerRole.STUDY_SERVER_OWNER.name())
                .query((rs, rowNum) -> new OwnerRole(
                        rs.getObject("user_id", UUID.class),
                        StudyServerRole.valueOf(rs.getString("role"))
                ))
                .single();
    }

    private List<StudyServerChannel> channelsFor(UUID studyServerId) {
        return jdbcClient.sql("""
                        SELECT id, study_server_id, name, kind, position
                        FROM study_server_channels
                        WHERE study_server_id = :studyServerId
                        ORDER BY position
                        """)
                .param("studyServerId", studyServerId)
                .query((rs, rowNum) -> mapStudyServerChannel(rs.getObject("id", UUID.class),
                        rs.getObject("study_server_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("kind"),
                        rs.getInt("position")))
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findDefaultVoiceChannelId(UUID studyServerId) {
        return jdbcClient.sql("""
                        SELECT id
                        FROM study_server_channels
                        WHERE study_server_id = :studyServerId
                        AND kind = :voiceKind
                        ORDER BY CASE WHEN name = 'study-room' THEN 0 ELSE 1 END, position
                        LIMIT 1
                        """)
                .param("studyServerId", studyServerId)
                .param("voiceKind", ChannelKind.VOICE.name())
                .query(UUID.class)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudyServerChannel> findChannelById(UUID channelId) {
        return jdbcClient.sql("""
                        SELECT id, study_server_id, name, kind, position
                        FROM study_server_channels
                        WHERE id = :channelId
                        """)
                .param("channelId", channelId)
                .query((rs, rowNum) -> mapStudyServerChannel(rs.getObject("id", UUID.class),
                        rs.getObject("study_server_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("kind"),
                        rs.getInt("position")))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isStudyServerMember(UUID studyServerId, UUID userId) {
        Integer membershipCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM (
                            SELECT ssr.user_id
                            FROM study_server_roles ssr
                            WHERE ssr.study_server_id = :studyServerId
                            AND ssr.user_id = :userId
                            UNION
                            SELECT cr.user_id
                            FROM courses co
                            JOIN course_roles cr ON cr.course_id = co.id
                            WHERE co.study_server_id = :studyServerId
                            AND cr.user_id = :userId
                            UNION
                            SELECT ce.learner_user_id
                            FROM courses co
                            JOIN cohorts c ON c.course_id = co.id
                            JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                            WHERE co.study_server_id = :studyServerId
                            AND ce.learner_user_id = :userId
                            UNION
                            SELECT cor.user_id
                            FROM courses co
                            JOIN cohorts c ON c.course_id = co.id
                            JOIN cohort_roles cor ON cor.cohort_id = c.id
                            WHERE co.study_server_id = :studyServerId
                            AND cor.user_id = :userId
                        ) memberships
                        """)
                .param("studyServerId", studyServerId)
                .param("userId", userId)
                .query(Integer.class)
                .single();

        return membershipCount > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean shareStudyServerMembership(UUID firstUserId, UUID secondUserId) {
        Integer sharedServerCount = jdbcClient.sql("""
                        SELECT COUNT(DISTINCT first_access.study_server_id)
                        FROM (
                            SELECT study_server_id
                            FROM study_server_roles
                            WHERE user_id = :firstUserId
                            UNION
                            SELECT co.study_server_id
                            FROM courses co
                            INNER JOIN course_roles cr ON cr.course_id = co.id
                            WHERE cr.user_id = :firstUserId
                            UNION
                            SELECT co.study_server_id
                            FROM courses co
                            INNER JOIN cohorts c ON c.course_id = co.id
                            INNER JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                            WHERE ce.learner_user_id = :firstUserId
                            UNION
                            SELECT co.study_server_id
                            FROM courses co
                            INNER JOIN cohorts c ON c.course_id = co.id
                            INNER JOIN cohort_roles cor ON cor.cohort_id = c.id
                            WHERE cor.user_id = :firstUserId
                        ) first_access
                        INNER JOIN (
                            SELECT study_server_id
                            FROM study_server_roles
                            WHERE user_id = :secondUserId
                            UNION
                            SELECT co.study_server_id
                            FROM courses co
                            INNER JOIN course_roles cr ON cr.course_id = co.id
                            WHERE cr.user_id = :secondUserId
                            UNION
                            SELECT co.study_server_id
                            FROM courses co
                            INNER JOIN cohorts c ON c.course_id = co.id
                            INNER JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                            WHERE ce.learner_user_id = :secondUserId
                            UNION
                            SELECT co.study_server_id
                            FROM courses co
                            INNER JOIN cohorts c ON c.course_id = co.id
                            INNER JOIN cohort_roles cor ON cor.cohort_id = c.id
                            WHERE cor.user_id = :secondUserId
                        ) second_access ON first_access.study_server_id = second_access.study_server_id
                        """)
                .param("firstUserId", firstUserId)
                .param("secondUserId", secondUserId)
                .query(Integer.class)
                .single();

        return sharedServerCount > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyServerMember> findMembers(UUID studyServerId) {
        return jdbcClient.sql("""
                        WITH membership_roles AS (
                            SELECT ssr.user_id AS user_id,
                                   CASE
                                       WHEN ssr.role = 'STUDY_SERVER_OWNER' THEN 'OWNER'
                                       ELSE 'MEMBER'
                                   END AS role,
                                   CASE
                                       WHEN ssr.role = 'STUDY_SERVER_OWNER' THEN 1
                                       ELSE 4
                                   END AS priority
                            FROM study_server_roles ssr
                            WHERE ssr.study_server_id = :studyServerId
                            UNION ALL
                            SELECT cr.user_id,
                                   'INSTRUCTOR',
                                   2
                            FROM courses co
                            JOIN course_roles cr ON cr.course_id = co.id
                            WHERE co.study_server_id = :studyServerId
                            UNION ALL
                            SELECT cor.user_id,
                                   'TA',
                                   3
                            FROM courses co
                            JOIN cohorts c ON c.course_id = co.id
                            JOIN cohort_roles cor ON cor.cohort_id = c.id
                            WHERE co.study_server_id = :studyServerId
                            UNION ALL
                            SELECT ce.learner_user_id,
                                   'LEARNER',
                                   5
                            FROM courses co
                            JOIN cohorts c ON c.course_id = co.id
                            JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                            WHERE co.study_server_id = :studyServerId
                        ),
                        ranked AS (
                            SELECT user_id,
                                   role,
                                   ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY priority ASC, role ASC) AS rn
                            FROM membership_roles
                        )
                        SELECT user_id, role
                        FROM ranked
                        WHERE rn = 1
                        ORDER BY
                            CASE role
                                WHEN 'OWNER' THEN 1
                                WHEN 'INSTRUCTOR' THEN 2
                                WHEN 'TA' THEN 3
                                WHEN 'MEMBER' THEN 4
                                ELSE 5
                            END,
                            user_id
                        """)
                .param("studyServerId", studyServerId)
                .query((rs, rowNum) -> new StudyServerMember(
                        rs.getObject("user_id", UUID.class),
                        rs.getString("role")
                ))
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public int countMembers(UUID studyServerId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM (
                            SELECT ssr.user_id
                            FROM study_server_roles ssr
                            WHERE ssr.study_server_id = :studyServerId
                            UNION
                            SELECT cr.user_id
                            FROM courses co
                            JOIN course_roles cr ON cr.course_id = co.id
                            WHERE co.study_server_id = :studyServerId
                            UNION
                            SELECT ce.learner_user_id
                            FROM courses co
                            JOIN cohorts c ON c.course_id = co.id
                            JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                            WHERE co.study_server_id = :studyServerId
                            UNION
                            SELECT cor.user_id
                            FROM courses co
                            JOIN cohorts c ON c.course_id = co.id
                            JOIN cohort_roles cor ON cor.cohort_id = c.id
                            WHERE co.study_server_id = :studyServerId
                        ) memberships
                        """)
                .param("studyServerId", studyServerId)
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CoMember> findCoMembers(UUID viewerUserId) {
        return jdbcClient.sql("""
                        WITH memberships AS (
                            SELECT user_id, study_server_id
                            FROM study_server_roles
                            UNION
                            SELECT cr.user_id, co.study_server_id
                            FROM courses co
                            INNER JOIN course_roles cr ON cr.course_id = co.id
                            UNION
                            SELECT ce.learner_user_id, co.study_server_id
                            FROM courses co
                            INNER JOIN cohorts c ON c.course_id = co.id
                            INNER JOIN cohort_enrollments ce ON ce.cohort_id = c.id
                            UNION
                            SELECT cor.user_id, co.study_server_id
                            FROM courses co
                            INNER JOIN cohorts c ON c.course_id = co.id
                            INNER JOIN cohort_roles cor ON cor.cohort_id = c.id
                        )
                        SELECT peer.user_id, MIN(ss.name) AS shared_study_server_name
                        FROM memberships viewer
                        INNER JOIN memberships peer
                            ON peer.study_server_id = viewer.study_server_id
                            AND peer.user_id <> viewer.user_id
                        INNER JOIN study_servers ss ON ss.id = peer.study_server_id
                        WHERE viewer.user_id = :viewerUserId
                        GROUP BY peer.user_id
                        ORDER BY MIN(ss.name), peer.user_id
                        """)
                .param("viewerUserId", viewerUserId)
                .query((rs, rowNum) -> new CoMember(
                        rs.getObject("user_id", UUID.class),
                        rs.getString("shared_study_server_name")
                ))
                .list();
    }

    @Override
    @Transactional
    public VoicePresence saveVoicePresence(UUID channelId, UUID memberUserId) {
        OffsetDateTime joinedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (usePostgresUpsert()) {
            jdbcClient.sql("""
                            INSERT INTO voice_channel_presences (channel_id, member_user_id, joined_at)
                            VALUES (:channelId, :memberUserId, :joinedAt)
                            ON CONFLICT (channel_id, member_user_id)
                            DO UPDATE SET joined_at = EXCLUDED.joined_at
                            """)
                    .param("channelId", channelId)
                    .param("memberUserId", memberUserId)
                    .param("joinedAt", joinedAt)
                    .update();
        } else {
            jdbcClient.sql("""
                            MERGE INTO voice_channel_presences (channel_id, member_user_id, joined_at)
                            KEY (channel_id, member_user_id)
                            VALUES (:channelId, :memberUserId, :joinedAt)
                            """)
                    .param("channelId", channelId)
                    .param("memberUserId", memberUserId)
                    .param("joinedAt", joinedAt)
                    .update();
        }

        // canSpeak/canListen are deferred until a moderation or media-token slice adds columns.
        return new VoicePresence(channelId, memberUserId, true, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoicePresence> findVoicePresences(UUID channelId) {
        return jdbcClient.sql("""
                        SELECT channel_id, member_user_id
                        FROM voice_channel_presences
                        WHERE channel_id = :channelId
                        ORDER BY joined_at, member_user_id
                        """)
                .param("channelId", channelId)
                .query((rs, rowNum) -> new VoicePresence(
                        rs.getObject("channel_id", UUID.class),
                        rs.getObject("member_user_id", UUID.class),
                        true, // deferred until capability columns exist
                        true
                ))
                .list();
    }

    @Override
    @Transactional
    public void deleteVoicePresence(UUID channelId, UUID memberUserId) {
        jdbcClient.sql("""
                        DELETE FROM voice_channel_presences
                        WHERE channel_id = :channelId
                        AND member_user_id = :memberUserId
                        """)
                .param("channelId", channelId)
                .param("memberUserId", memberUserId)
                .update();
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        jdbcClient.sql("""
                        DELETE FROM study_servers
                        WHERE id = :id
                        """)
                .param("id", id)
                .update();
    }

    @Override
    @Transactional
    public StudyServerInvitation saveInvitation(StudyServerInvitation invitation) {
        jdbcClient.sql("""
                        INSERT INTO study_server_invitations (
                            id,
                            study_server_id,
                            invited_user_id,
                            email,
                            invited_by_user_id,
                            status,
                            created_at
                        )
                        VALUES (
                            :id,
                            :studyServerId,
                            :invitedUserId,
                            :email,
                            :invitedByUserId,
                            :status,
                            :createdAt
                        )
                        """)
                .param("id", invitation.id())
                .param("studyServerId", invitation.studyServerId())
                .param("invitedUserId", invitation.invitedUserId())
                .param("email", invitation.email())
                .param("invitedByUserId", invitation.invitedByUserId())
                .param("status", invitation.status().name())
                .param("createdAt", OffsetDateTime.ofInstant(invitation.createdAt(), ZoneOffset.UTC))
                .update();
        return invitation;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyServerInvitation> findPendingInvitations(UUID studyServerId) {
        return jdbcClient.sql("""
                        SELECT id,
                               study_server_id,
                               invited_user_id,
                               email,
                               invited_by_user_id,
                               status,
                               created_at,
                               resolved_at
                        FROM study_server_invitations
                        WHERE study_server_id = :studyServerId
                        AND status = :status
                        ORDER BY created_at ASC, id ASC
                        """)
                .param("studyServerId", studyServerId)
                .param("status", StudyServerInvitationStatus.PENDING.name())
                .query(this::mapStudyServerInvitation)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyServerInvitation> findPendingInvitationsForUser(UUID invitedUserId) {
        return jdbcClient.sql("""
                        SELECT id,
                               study_server_id,
                               invited_user_id,
                               email,
                               invited_by_user_id,
                               status,
                               created_at,
                               resolved_at
                        FROM study_server_invitations
                        WHERE invited_user_id = :invitedUserId
                        AND status = :status
                        ORDER BY created_at ASC, id ASC
                        """)
                .param("invitedUserId", invitedUserId)
                .param("status", StudyServerInvitationStatus.PENDING.name())
                .query(this::mapStudyServerInvitation)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudyServerInvitation> findInvitation(UUID studyServerId, UUID invitationId) {
        return jdbcClient.sql("""
                        SELECT id,
                               study_server_id,
                               invited_user_id,
                               email,
                               invited_by_user_id,
                               status,
                               created_at,
                               resolved_at
                        FROM study_server_invitations
                        WHERE study_server_id = :studyServerId
                        AND id = :invitationId
                        """)
                .param("studyServerId", studyServerId)
                .param("invitationId", invitationId)
                .query(this::mapStudyServerInvitation)
                .optional();
    }

    @Override
    @Transactional
    public void acceptInvitation(
            UUID studyServerId,
            UUID invitationId,
            UUID acceptedByUserId,
            Instant resolvedAt
    ) {
        jdbcClient.sql("""
                        UPDATE study_server_invitations
                        SET status = :status,
                            resolved_at = :resolvedAt
                        WHERE study_server_id = :studyServerId
                        AND id = :invitationId
                        AND invited_user_id = :acceptedByUserId
                        AND status = :pendingStatus
                        """)
                .param("status", StudyServerInvitationStatus.ACCEPTED.name())
                .param("resolvedAt", OffsetDateTime.ofInstant(resolvedAt, ZoneOffset.UTC))
                .param("studyServerId", studyServerId)
                .param("invitationId", invitationId)
                .param("acceptedByUserId", acceptedByUserId)
                .param("pendingStatus", StudyServerInvitationStatus.PENDING.name())
                .update();

        try {
            jdbcClient.sql("""
                            INSERT INTO study_server_roles (study_server_id, user_id, role)
                            VALUES (:studyServerId, :userId, :role)
                            """)
                    .param("studyServerId", studyServerId)
                    .param("userId", acceptedByUserId)
                    .param("role", StudyServerRole.STUDY_SERVER_MEMBER.name())
                    .update();
        } catch (DuplicateKeyException ignored) {
            // Accepting again after membership already exists is idempotent.
        }
    }

    private StudyServerInvitation mapStudyServerInvitation(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        OffsetDateTime resolvedAt = rs.getObject("resolved_at", OffsetDateTime.class);
        return new StudyServerInvitation(
                rs.getObject("id", UUID.class),
                rs.getObject("study_server_id", UUID.class),
                rs.getObject("invited_user_id", UUID.class),
                rs.getString("email"),
                rs.getObject("invited_by_user_id", UUID.class),
                StudyServerInvitationStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                resolvedAt == null ? null : resolvedAt.toInstant()
        );
    }

    private StudyServerChannel mapStudyServerChannel(
            UUID id,
            UUID studyServerId,
            String name,
            String kind,
            int position
    ) {
        return new StudyServerChannel(id, studyServerId, name, ChannelKind.valueOf(kind), position);
    }
}
