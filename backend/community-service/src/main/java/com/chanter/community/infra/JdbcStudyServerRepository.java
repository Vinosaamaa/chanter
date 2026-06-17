package com.chanter.community.infra;

import com.chanter.community.application.StudyServerRepository;
import com.chanter.community.domain.ChannelKind;
import com.chanter.community.domain.OwnerRole;
import com.chanter.community.domain.StudyServer;
import com.chanter.community.domain.StudyServerChannel;
import com.chanter.community.domain.StudyServerRole;
import com.chanter.community.domain.VoicePresence;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcStudyServerRepository implements StudyServerRepository {

    private final JdbcClient jdbcClient;

    public JdbcStudyServerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public StudyServer save(StudyServer studyServer) {
        jdbcClient.sql("""
                        INSERT INTO study_servers (id, name, owner_user_id, created_at)
                        VALUES (:id, :name, :ownerUserId, :createdAt)
                        """)
                .param("id", studyServer.id())
                .param("name", studyServer.name())
                .param("ownerUserId", studyServer.ownerRole().userId())
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
                        SELECT id, name, owner_user_id, created_at
                        FROM study_servers
                        WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> new StudyServer(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        ownerRoleFor(id),
                        channelsFor(id),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()
                ))
                .optional();
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
        Integer roleCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM study_server_roles
                        WHERE study_server_id = :studyServerId
                        AND user_id = :userId
                        """)
                .param("studyServerId", studyServerId)
                .param("userId", userId)
                .query(Integer.class)
                .single();

        return roleCount > 0;
    }

    @Override
    @Transactional
    public VoicePresence saveVoicePresence(UUID channelId, UUID memberUserId) {
        deleteVoicePresence(channelId, memberUserId);

        jdbcClient.sql("""
                        INSERT INTO voice_channel_presences (channel_id, member_user_id, joined_at)
                        VALUES (:channelId, :memberUserId, :joinedAt)
                        """)
                .param("channelId", channelId)
                .param("memberUserId", memberUserId)
                .param("joinedAt", OffsetDateTime.now(ZoneOffset.UTC))
                .update();

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
