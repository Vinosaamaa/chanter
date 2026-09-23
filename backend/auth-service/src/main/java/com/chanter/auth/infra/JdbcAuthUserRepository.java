package com.chanter.auth.infra;

import com.chanter.auth.application.AuthUserRepository;
import com.chanter.auth.domain.AuthUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAuthUserRepository implements AuthUserRepository {

    private static final RowMapper<AuthUser> ROW_MAPPER = (resultSet, rowNum) -> mapRow(resultSet);

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuthUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.NESTED)
    public AuthUser save(AuthUser user) {
        // A concurrent signup may win the unique email. Roll back only this insert so its caller
        // can read that account and enqueue the neutral response email in the outer transaction.
        jdbcTemplate.update(
                """
                INSERT INTO auth_users (id, email, password_hash, display_name, email_verified, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                user.id(),
                user.email(),
                user.passwordHash(),
                user.displayName(),
                user.emailVerified(),
                Timestamp.from(user.createdAt())
        );
        return user;
    }

    @Override
    @Transactional
    public AuthUser update(AuthUser user) {
        requireActiveWrite(jdbcTemplate, user.id());
        jdbcTemplate.update(
                """
                UPDATE auth_users
                SET email = ?, password_hash = ?, display_name = ?, email_verified = ?
                WHERE id = ?
                """,
                user.email(),
                user.passwordHash(),
                user.displayName(),
                user.emailVerified(),
                user.id()
        );
        return user;
    }

    @Override
    public Optional<AuthUser> findByEmail(String email) {
        return jdbcTemplate.query(
                        """
                        SELECT id, email, password_hash, display_name, email_verified, created_at
                        FROM auth_users
                        WHERE email = ?
                        """,
                        ROW_MAPPER,
                        email
                )
                .stream()
                .findFirst();
    }

    @Override
    public Optional<AuthUser> findById(UUID id) {
        return jdbcTemplate.query(
                        """
                        SELECT id, email, password_hash, display_name, email_verified, created_at
                        FROM auth_users
                        WHERE id = ?
                        """,
                        ROW_MAPPER,
                        id
                )
                .stream()
                .findFirst();
    }

    @Override
    public List<AuthUser> findByIds(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        String placeholders = ids.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        return jdbcTemplate.query(
                """
                SELECT id, email, password_hash, display_name, email_verified, created_at
                FROM auth_users
                WHERE id IN (%s)
                """.formatted(placeholders),
                ROW_MAPPER,
                ids.toArray()
        );
    }

    @Override
    public boolean existsByEmail(String email) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM auth_users WHERE email = ?)",
                Boolean.class,
                email
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    @Transactional
    public void markEmailVerified(UUID userId) {
        requireActiveWrite(jdbcTemplate, userId);
        jdbcTemplate.update("UPDATE auth_users SET email_verified = TRUE WHERE id = ?", userId);
    }

    @Override
    @Transactional
    public void updatePasswordHash(UUID userId, String passwordHash) {
        requireActiveWrite(jdbcTemplate, userId);
        jdbcTemplate.update("UPDATE auth_users SET password_hash = ? WHERE id = ?", passwordHash, userId);
    }

    @Override
    public boolean lockActive(UUID id) { return lockActiveWrite(jdbcTemplate, id); }

    static boolean lockActiveWrite(JdbcTemplate jdbc, UUID id) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Account writes require an owning transaction");
        if (jdbc.query("SELECT id FROM auth_users WHERE id=? FOR UPDATE", (rs,row) -> rs.getObject(1), id).isEmpty()) return false;
        return !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM lifecycle_terminal_journal WHERE target_kind='ACCOUNT' AND target_id=?)", Boolean.class, id));
    }

    static void requireActiveWrite(JdbcTemplate jdbc, UUID id) {
        if (!lockActiveWrite(jdbc,id)) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.GONE,"Account is unavailable");
    }

    private static AuthUser mapRow(ResultSet resultSet) throws SQLException {
        return new AuthUser(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                resultSet.getString("display_name"),
                resultSet.getBoolean("email_verified"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }
}
