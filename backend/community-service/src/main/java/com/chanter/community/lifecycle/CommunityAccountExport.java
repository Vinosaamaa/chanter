package com.chanter.community.lifecycle;

import com.chanter.common.lifecycle.AccountExportProjection;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.chanter.common.lifecycle.JdbcExportRows;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Authored content and the person's own membership records, never a server-wide member dump. */
@Component
public final class CommunityAccountExport implements AccountExportProjection {
    private final JdbcTemplate jdbc;
    public CommunityAccountExport(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void capture(UUID accountId, ExportSnapshotStore.Capture output) throws IOException {
        JdbcExportRows.write(jdbc, output, "owned_study_servers", """
            SELECT id,name,description,server_type,plan_tier,created_at FROM study_servers WHERE owner_user_id=? ORDER BY id
            """, accountId);
        JdbcExportRows.write(jdbc, output, "server_memberships", "SELECT study_server_id,role FROM study_server_roles WHERE user_id=? ORDER BY study_server_id,role", accountId);
        JdbcExportRows.write(jdbc, output, "authored_courses", """
            SELECT id,study_server_id,title,description,published,archived_at,created_at FROM courses WHERE instructor_user_id=? ORDER BY id
            """, accountId);
        JdbcExportRows.write(jdbc, output, "course_memberships", "SELECT course_id,role FROM course_roles WHERE user_id=? ORDER BY course_id,role", accountId);
        JdbcExportRows.write(jdbc, output, "cohort_memberships", "SELECT cohort_id,role FROM cohort_roles WHERE user_id=? ORDER BY cohort_id,role", accountId);
        JdbcExportRows.write(jdbc, output, "cohort_enrollments", """
            SELECT cohort_id,enrolled_by_user_id,assigned_ta_user_id,enrolled_at FROM cohort_enrollments WHERE learner_user_id=? ORDER BY cohort_id
            """, accountId);
        JdbcExportRows.write(jdbc, output, "server_invitations", """
            SELECT id,study_server_id,invited_user_id,invited_by_user_id,status,created_at,resolved_at,
                CASE WHEN invited_user_id=? THEN email ELSE NULL END AS own_email
            FROM study_server_invitations WHERE invited_user_id=? OR invited_by_user_id=? ORDER BY id
            """, accountId, accountId, accountId);
        JdbcExportRows.write(jdbc, output, "cohort_invitations", """
            SELECT id,cohort_id,invited_user_id,invited_by_user_id,status,created_at,resolved_at,
                CASE WHEN invited_user_id=? THEN email ELSE NULL END AS own_email
            FROM cohort_invitations WHERE invited_user_id=? OR invited_by_user_id=? ORDER BY id
            """, accountId, accountId, accountId);
        JdbcExportRows.write(jdbc, output, "authored_announcements", """
            SELECT id,study_server_id,title,body,status,created_at,updated_at,archived_at FROM community_announcements WHERE author_user_id=? ORDER BY id
            """, accountId);
        JdbcExportRows.write(jdbc, output, "announcement_reactions", "SELECT announcement_id,kind,updated_at FROM community_announcement_reactions WHERE user_id=? ORDER BY announcement_id,kind", accountId);
        JdbcExportRows.write(jdbc, output, "authored_events", """
            SELECT id,study_server_id,title,description,location,starts_at,ends_at,capacity,visibility,course_id,cohort_id,status,created_at,updated_at
            FROM community_events WHERE created_by_user_id=? ORDER BY id
            """, accountId);
        JdbcExportRows.write(jdbc, output, "event_responses", "SELECT event_id,status,updated_at FROM community_event_rsvps WHERE user_id=? ORDER BY event_id", accountId);
        JdbcExportRows.write(jdbc, output, "scheduled_office_hours", """
            SELECT id,cohort_id,voice_channel_id,starts_at,ends_at,status,created_at FROM office_hours_sessions WHERE scheduled_by_user_id=? ORDER BY id
            """, accountId);
        JdbcExportRows.write(jdbc, output, "office_hours_waitlist", "SELECT session_id,joined_at,status FROM office_hours_waitlist_entries WHERE learner_user_id=? ORDER BY session_id", accountId);
        JdbcExportRows.write(jdbc, output, "office_hours_participation", """
            SELECT session_id,can_speak,hand_raised,active,joined_at,updated_at FROM office_hours_participants WHERE user_id=? ORDER BY session_id
            """, accountId);
        output.jsonLines("coverage", rows -> rows.add(Map.of(
                "billing", "Owned Study Server plan metadata is included; this service has no payment or invoice ledger.",
                "omitted", List.of("Other members' profiles and private content", "Invitation access codes", "Other people's email addresses"))));
    }
}
