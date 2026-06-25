package com.chanter.auth.infra;

import com.chanter.auth.application.RefreshTokenRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRefreshTokenRepository implements RefreshTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
        jdbcTemplate.update(
                """
                INSERT INTO auth_refresh_tokens (id, user_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """,
                id,
                userId,
                tokenHash,
                Timestamp.from(expiresAt)
        );
    }

    @Override
    public Optional<UUID> findActiveUserIdByTokenHash(String tokenHash, Instant now) {
        return jdbcTemplate.query(
                        """
                        SELECT user_id
                        FROM auth_refresh_tokens
                        WHERE token_hash = ?
                          AND revoked_at IS NULL
                          AND expires_at > ?
                        """,
                        (resultSet, rowNum) -> resultSet.getObject("user_id", UUID.class),
                        tokenHash,
                        Timestamp.from(now)
                )
                .stream()
                .findFirst();
    }

    @Override
    public Optional<UUID> consumeActiveUserIdByTokenHash(String tokenHash, Instant now) {
        List<UUID> userIds = jdbcTemplate.query(
                        """
                        SELECT user_id
                        FROM auth_refresh_tokens
                        WHERE token_hash = ?
                          AND revoked_at IS NULL
                          AND expires_at > ?
                        FOR UPDATE
                        """,
                        (resultSet, rowNum) -> resultSet.getObject("user_id", UUID.class),
                        tokenHash,
                        Timestamp.from(now)
                );

        if (userIds.isEmpty()) {
            return Optional.empty();
        }

        int revokedRows = jdbcTemplate.update(
                """
                UPDATE auth_refresh_tokens
                SET revoked_at = ?
                WHERE token_hash = ?
                  AND revoked_at IS NULL
                """,
                Timestamp.from(now),
                tokenHash
        );

        if (revokedRows == 0) {
            return Optional.empty();
        }

        return Optional.of(userIds.getFirst());
    }

    @Override
    public void revokeByTokenHash(String tokenHash, Instant revokedAt) {
        jdbcTemplate.update(
                """
                UPDATE auth_refresh_tokens
                SET revoked_at = ?
                WHERE token_hash = ?
                  AND revoked_at IS NULL
                """,
                Timestamp.from(revokedAt),
                tokenHash
        );
    }

    @Override
    public void revokeAllForUser(UUID userId, Instant revokedAt) {
        jdbcTemplate.update(
                """
                UPDATE auth_refresh_tokens
                SET revoked_at = ?
                WHERE user_id = ?
                  AND revoked_at IS NULL
                """,
                Timestamp.from(revokedAt),
                userId
        );
    }
}
