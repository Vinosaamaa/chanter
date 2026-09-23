package com.chanter.auth.lifecycle;

import com.chanter.common.lifecycle.AccountExportProjection;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.chanter.common.lifecycle.JdbcExportRows;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class AuthAccountExport implements AccountExportProjection {
    private final JdbcTemplate jdbc;
    public AuthAccountExport(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void capture(UUID accountId, ExportSnapshotStore.Capture output) throws IOException {
        JdbcExportRows.write(jdbc, output, "profile", """
            SELECT id,email,display_name,email_verified,created_at FROM auth_users WHERE id=?
            """, accountId);
        JdbcExportRows.write(jdbc, output, "linked_accounts", """
            SELECT provider,provider_subject,created_at FROM auth_oauth_accounts WHERE user_id=? ORDER BY provider,id
            """, accountId);
        JdbcExportRows.write(jdbc, output, "sessions", """
            SELECT created_at,last_used_at,expires_at,revoked_at,user_agent FROM auth_sessions WHERE user_id=? ORDER BY created_at,id
            """, accountId);
        output.jsonLines("coverage", rows -> rows.add(Map.of(
                "included", java.util.List.of("profile", "linked_accounts", "sessions"),
                "omitted", java.util.List.of("Passwords and authentication credentials", "Reset and verification links", "Operator-only evidence"),
                "capture", "Auth service snapshot; other services report their own capture time")));
    }
}
