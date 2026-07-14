package com.chanter.auth.infra;

import com.chanter.auth.application.AuthUserRepository;
import com.chanter.auth.domain.AuthUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuthUserRepository implements AuthUserRepository {

    private static final RowMapper<AuthUser> ROW_MAPPER = (resultSet, rowNum) -> mapRow(resultSet);

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuthUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AuthUser save(AuthUser user) {
        jdbcTemplate.update(
                """
                INSERT INTO auth_users (id, email, password_hash, display_name, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                user.id(),
                user.email(),
                user.passwordHash(),
                user.displayName(),
                Timestamp.from(user.createdAt())
        );
        return user;
    }

    @Override
    public Optional<AuthUser> findByEmail(String email) {
        return jdbcTemplate.query(
                        """
                        SELECT id, email, password_hash, display_name, created_at
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
                        SELECT id, email, password_hash, display_name, created_at
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
                SELECT id, email, password_hash, display_name, created_at
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

    private static AuthUser mapRow(ResultSet resultSet) throws SQLException {
        return new AuthUser(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                resultSet.getString("display_name"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }
}
