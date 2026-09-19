package com.chanter.auth.application;

import static org.assertj.core.api.Assertions.*;

import com.chanter.auth.lifecycle.LifecycleSessionAccess;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:lifecycle-oauth-recent;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "chanter.oauth.google.client-id=fixture-client", "chanter.oauth.google.client-secret=fixture-secret"})
@ActiveProfiles("test")
class LifecycleOAuthRecentAuthTest {
    @Autowired OAuthAuthService oauth;
    @Autowired AuthSessionService sessions;
    @Autowired LifecycleSessionAccess access;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Test void verifiedProviderCallbackCreatesFreshSessionButPassiveRefreshCannotRenewProof() {
        // This is the existing post-provider-verification boundary; no provider network request or login occurs in this test.
        var claims = Map.<String,Object>of("sub", "fixture-" + UUID.randomUUID(), "email", UUID.randomUUID() + "@example.invalid",
                "name", "OAuth lifecycle fixture", "email_verified", true);
        var first = oauth.sessionFromGoogleUserInfo(claims);
        var tx = new TransactionTemplate(transactions);
        var verified = tx.execute(status -> access.require("Bearer " + first.accessToken(), true));
        assertThat(verified).isEqualTo(first.user().id());
        jdbc.update("UPDATE auth_sessions SET created_at=? WHERE user_id=?", Timestamp.from(Instant.now().minusSeconds(301)), first.user().id());
        var refreshed = sessions.refresh(first.refreshToken());
        assertThatThrownBy(() -> tx.execute(status -> access.require("Bearer " + refreshed.accessToken(), true))).hasMessageContaining("RECENT_LOGIN_REQUIRED");
        var relogged = oauth.sessionFromGoogleUserInfo(claims);
        var fresh = tx.execute(status -> access.require("Bearer " + relogged.accessToken(), true));
        assertThat(fresh).isEqualTo(first.user().id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id=?", Integer.class, first.user().id())).isEqualTo(2);
    }
}
