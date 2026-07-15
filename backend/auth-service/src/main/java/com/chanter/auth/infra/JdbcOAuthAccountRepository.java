package com.chanter.auth.infra;

import com.chanter.auth.application.OAuthAccountRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOAuthAccountRepository implements OAuthAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOAuthAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UUID> findUserId(String provider, String providerSubject) {
        return jdbcTemplate.query(
                        """
                        SELECT user_id
                        FROM auth_oauth_accounts
                        WHERE provider = ? AND provider_subject = ?
                        """,
                        (rs, rowNum) -> rs.getObject("user_id", UUID.class),
                        provider,
                        providerSubject
                )
                .stream()
                .findFirst();
    }

    @Override
    public void link(UUID id, UUID userId, String provider, String providerSubject) {
        jdbcTemplate.update(
                """
                INSERT INTO auth_oauth_accounts (id, user_id, provider, provider_subject, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                id,
                userId,
                provider,
                providerSubject,
                Timestamp.from(Instant.now())
        );
    }
}
