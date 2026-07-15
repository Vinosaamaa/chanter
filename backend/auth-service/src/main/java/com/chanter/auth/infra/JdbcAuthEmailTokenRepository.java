package com.chanter.auth.infra;

import com.chanter.auth.application.AuthEmailTokenRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuthEmailTokenRepository implements AuthEmailTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuthEmailTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(UUID id, UUID userId, String tokenHash, String purpose, Instant expiresAt) {
        jdbcTemplate.update(
                """
                INSERT INTO auth_email_tokens (id, user_id, token_hash, purpose, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                userId,
                tokenHash,
                purpose,
                Timestamp.from(expiresAt),
                Timestamp.from(Instant.now())
        );
    }

    @Override
    public Optional<TokenRecord> findActiveByTokenHash(String tokenHash, String purpose, Instant now) {
        return jdbcTemplate.query(
                        """
                        SELECT id, user_id, expires_at
                        FROM auth_email_tokens
                        WHERE token_hash = ?
                          AND purpose = ?
                          AND used_at IS NULL
                          AND expires_at > ?
                        """,
                        (rs, rowNum) -> new TokenRecord(
                                rs.getObject("id", UUID.class),
                                rs.getObject("user_id", UUID.class),
                                rs.getTimestamp("expires_at").toInstant()
                        ),
                        tokenHash,
                        purpose,
                        Timestamp.from(now)
                )
                .stream()
                .findFirst();
    }

    @Override
    public void markUsed(UUID id, Instant usedAt) {
        jdbcTemplate.update("UPDATE auth_email_tokens SET used_at = ? WHERE id = ?", Timestamp.from(usedAt), id);
    }

    @Override
    public void invalidateActiveForUser(UUID userId, String purpose, Instant usedAt) {
        jdbcTemplate.update(
                """
                UPDATE auth_email_tokens
                SET used_at = ?
                WHERE user_id = ? AND purpose = ? AND used_at IS NULL
                """,
                Timestamp.from(usedAt),
                userId,
                purpose
        );
    }
}
