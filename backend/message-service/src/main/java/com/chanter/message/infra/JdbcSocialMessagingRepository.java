package com.chanter.message.infra;

import com.chanter.message.application.SocialMessagingRepository;
import com.chanter.message.domain.DirectMessage;
import com.chanter.message.domain.FriendRequest;
import com.chanter.message.domain.FriendRequestStatus;
import com.chanter.message.domain.FriendSummary;
import com.chanter.message.domain.FriendshipSnapshot;
import com.chanter.message.domain.FriendshipState;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcSocialMessagingRepository implements SocialMessagingRepository {

    private final JdbcClient jdbcClient;
    private final DataSource dataSource;
    private volatile Boolean postgresDatabase;

    public JdbcSocialMessagingRepository(JdbcClient jdbcClient, DataSource dataSource) {
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
    public FriendRequest saveFriendRequest(FriendRequest friendRequest) {
        jdbcClient.sql("""
                        INSERT INTO friend_requests (id, sender_user_id, recipient_user_id, status, created_at)
                        VALUES (:id, :senderUserId, :recipientUserId, :status, :createdAt)
                        """)
                .param("id", friendRequest.id())
                .param("senderUserId", friendRequest.senderUserId())
                .param("recipientUserId", friendRequest.recipientUserId())
                .param("status", friendRequest.status().name())
                .param("createdAt", toOffsetDateTime(friendRequest.createdAt()))
                .update();

        return friendRequest;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FriendRequest> findFriendRequestById(UUID friendRequestId) {
        return jdbcClient.sql("""
                        SELECT id, sender_user_id, recipient_user_id, status, created_at
                        FROM friend_requests
                        WHERE id = :friendRequestId
                        """)
                .param("friendRequestId", friendRequestId)
                .query((rs, rowNum) -> new FriendRequest(
                        rs.getObject("id", UUID.class),
                        rs.getObject("sender_user_id", UUID.class),
                        rs.getObject("recipient_user_id", UUID.class),
                        FriendRequestStatus.valueOf(rs.getString("status")),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()
                ))
                .optional();
    }

    @Override
    @Transactional
    public Optional<FriendRequest> updateFriendRequestStatus(UUID friendRequestId, FriendRequestStatus status) {
        int updatedRows = jdbcClient.sql("""
                        UPDATE friend_requests
                        SET status = :status
                        WHERE id = :friendRequestId
                        AND status = 'PENDING'
                        """)
                .param("status", status.name())
                .param("friendRequestId", friendRequestId)
                .update();

        if (updatedRows == 0) {
            return Optional.empty();
        }

        return findFriendRequestById(friendRequestId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean areFriends(UUID firstUserId, UUID secondUserId) {
        Integer acceptedCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM friend_requests
                        WHERE status = 'ACCEPTED'
                        AND (
                            (sender_user_id = :firstUserId AND recipient_user_id = :secondUserId)
                            OR (sender_user_id = :secondUserId AND recipient_user_id = :firstUserId)
                        )
                        """)
                .param("firstUserId", firstUserId)
                .param("secondUserId", secondUserId)
                .query(Integer.class)
                .single();

        return acceptedCount > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPendingFriendRequest(UUID firstUserId, UUID secondUserId) {
        Integer pendingCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM friend_requests
                        WHERE status = 'PENDING'
                        AND (
                            (sender_user_id = :firstUserId AND recipient_user_id = :secondUserId)
                            OR (sender_user_id = :secondUserId AND recipient_user_id = :firstUserId)
                        )
                        """)
                .param("firstUserId", firstUserId)
                .param("secondUserId", secondUserId)
                .query(Integer.class)
                .single();

        return pendingCount > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public FriendshipSnapshot findFriendshipSnapshot(UUID firstUserId, UUID secondUserId) {
        if (areFriends(firstUserId, secondUserId)) {
            return jdbcClient.sql("""
                            SELECT id, sender_user_id, recipient_user_id
                            FROM friend_requests
                            WHERE status = 'ACCEPTED'
                            AND (
                                (sender_user_id = :firstUserId AND recipient_user_id = :secondUserId)
                                OR (sender_user_id = :secondUserId AND recipient_user_id = :firstUserId)
                            )
                            ORDER BY created_at DESC
                            LIMIT 1
                            """)
                    .param("firstUserId", firstUserId)
                    .param("secondUserId", secondUserId)
                    .query((rs, rowNum) -> new FriendshipSnapshot(
                            FriendshipState.ACCEPTED,
                            Optional.of(rs.getObject("id", UUID.class)),
                            Optional.of(rs.getObject("sender_user_id", UUID.class)),
                            Optional.of(rs.getObject("recipient_user_id", UUID.class))
                    ))
                    .optional()
                    .orElseGet(() -> new FriendshipSnapshot(
                            FriendshipState.ACCEPTED,
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty()
                    ));
        }

        return jdbcClient.sql("""
                        SELECT id, sender_user_id, recipient_user_id
                        FROM friend_requests
                        WHERE status = 'PENDING'
                        AND (
                            (sender_user_id = :firstUserId AND recipient_user_id = :secondUserId)
                            OR (sender_user_id = :secondUserId AND recipient_user_id = :firstUserId)
                        )
                        ORDER BY created_at DESC
                        LIMIT 1
                        """)
                .param("firstUserId", firstUserId)
                .param("secondUserId", secondUserId)
                .query((rs, rowNum) -> new FriendshipSnapshot(
                        FriendshipState.PENDING,
                        Optional.of(rs.getObject("id", UUID.class)),
                        Optional.of(rs.getObject("sender_user_id", UUID.class)),
                        Optional.of(rs.getObject("recipient_user_id", UUID.class))
                ))
                .optional()
                .orElse(FriendshipSnapshot.none());
    }

    @Override
    @Transactional
    public void removeFriendship(UUID firstUserId, UUID secondUserId) {
        jdbcClient.sql("""
                        DELETE FROM friend_requests
                        WHERE status = 'ACCEPTED'
                        AND (
                            (sender_user_id = :firstUserId AND recipient_user_id = :secondUserId)
                            OR (sender_user_id = :secondUserId AND recipient_user_id = :firstUserId)
                        )
                        """)
                .param("firstUserId", firstUserId)
                .param("secondUserId", secondUserId)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBlocked(UUID senderUserId, UUID recipientUserId) {
        Integer blockCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM user_blocks
                        WHERE (blocker_user_id = :recipientUserId AND blocked_user_id = :senderUserId)
                        OR (blocker_user_id = :senderUserId AND blocked_user_id = :recipientUserId)
                        """)
                .param("senderUserId", senderUserId)
                .param("recipientUserId", recipientUserId)
                .query(Integer.class)
                .single();

        return blockCount > 0;
    }

    @Override
    @Transactional
    public void saveUserBlock(UUID blockerUserId, UUID blockedUserId) {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (usePostgresUpsert()) {
            jdbcClient.sql("""
                            INSERT INTO user_blocks (blocker_user_id, blocked_user_id, created_at)
                            VALUES (:blockerUserId, :blockedUserId, :createdAt)
                            ON CONFLICT (blocker_user_id, blocked_user_id) DO NOTHING
                            """)
                    .param("blockerUserId", blockerUserId)
                    .param("blockedUserId", blockedUserId)
                    .param("createdAt", createdAt)
                    .update();
            return;
        }

        jdbcClient.sql("""
                        MERGE INTO user_blocks (blocker_user_id, blocked_user_id, created_at)
                        KEY (blocker_user_id, blocked_user_id)
                        VALUES (:blockerUserId, :blockedUserId, :createdAt)
                        """)
                .param("blockerUserId", blockerUserId)
                .param("blockedUserId", blockedUserId)
                .param("createdAt", createdAt)
                .update();
    }

    @Override
    @Transactional
    public DirectMessage saveDirectMessage(DirectMessage directMessage) {
        jdbcClient.sql("""
                        INSERT INTO direct_messages (id, sender_user_id, recipient_user_id, body, sent_at)
                        VALUES (:id, :senderUserId, :recipientUserId, :body, :sentAt)
                        """)
                .param("id", directMessage.id())
                .param("senderUserId", directMessage.senderUserId())
                .param("recipientUserId", directMessage.recipientUserId())
                .param("body", directMessage.body())
                .param("sentAt", toOffsetDateTime(directMessage.sentAt()))
                .update();

        return directMessage;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectMessage> findDirectMessages(UUID viewerUserId, UUID peerUserId) {
        return jdbcClient.sql("""
                        SELECT id, sender_user_id, recipient_user_id, body, sent_at
                        FROM direct_messages
                        WHERE (sender_user_id = :viewerUserId AND recipient_user_id = :peerUserId)
                        OR (sender_user_id = :peerUserId AND recipient_user_id = :viewerUserId)
                        ORDER BY sent_at, id
                        """)
                .param("viewerUserId", viewerUserId)
                .param("peerUserId", peerUserId)
                .query((rs, rowNum) -> new DirectMessage(
                        rs.getObject("id", UUID.class),
                        rs.getObject("sender_user_id", UUID.class),
                        rs.getObject("recipient_user_id", UUID.class),
                        rs.getString("body"),
                        rs.getObject("sent_at", OffsetDateTime.class).toInstant()
                ))
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendSummary> findFriends(UUID viewerUserId) {
        return jdbcClient.sql("""
                        SELECT
                            CASE
                                WHEN sender_user_id = :viewerUserId THEN recipient_user_id
                                ELSE sender_user_id
                            END AS friend_user_id,
                            created_at AS friends_since
                        FROM friend_requests fr
                        WHERE fr.status = 'ACCEPTED'
                        AND (fr.sender_user_id = :viewerUserId OR fr.recipient_user_id = :viewerUserId)
                        AND NOT EXISTS (
                            SELECT 1
                            FROM user_blocks ub
                            WHERE (ub.blocker_user_id = :viewerUserId AND ub.blocked_user_id = CASE
                                    WHEN fr.sender_user_id = :viewerUserId THEN fr.recipient_user_id
                                    ELSE fr.sender_user_id
                                END)
                            OR (ub.blocker_user_id = CASE
                                    WHEN fr.sender_user_id = :viewerUserId THEN fr.recipient_user_id
                                    ELSE fr.sender_user_id
                                END AND ub.blocked_user_id = :viewerUserId)
                        )
                        ORDER BY friends_since, friend_user_id
                        """)
                .param("viewerUserId", viewerUserId)
                .query((rs, rowNum) -> new FriendSummary(
                        rs.getObject("friend_user_id", UUID.class),
                        rs.getObject("friends_since", OffsetDateTime.class).toInstant()
                ))
                .list();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
