package com.chanter.community.lifecycle;

import com.chanter.common.lifecycle.TerminalJournal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Erases personal community payloads; keeps exact IDs needed for downstream deletion receipts. */
@Component
public final class CommunityAccountCleanup {
    private final JdbcTemplate jdbc;
    public CommunityAccountCleanup(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public void erase(TerminalJournal.Entry entry) {
        UUID account=entry.targetId();
        retain(entry,"ANNOUNCEMENT","SELECT id FROM community_announcements WHERE author_user_id=?");
        retain(entry,"EVENT","SELECT id FROM community_events WHERE created_by_user_id=?");
        retain(entry,"OFFICE_HOURS","SELECT id FROM office_hours_sessions WHERE scheduled_by_user_id=?");
        jdbc.update("DELETE FROM community_announcements WHERE author_user_id=?",account);
        jdbc.update("DELETE FROM community_events WHERE created_by_user_id=?",account);
        jdbc.update("DELETE FROM office_hours_sessions WHERE scheduled_by_user_id=?",account);
        jdbc.update("DELETE FROM community_announcement_reactions WHERE user_id=?",account);
        jdbc.update("DELETE FROM community_event_rsvps WHERE user_id=?",account);
        jdbc.update("DELETE FROM office_hours_waitlist_entries WHERE learner_user_id=?",account);
        jdbc.update("DELETE FROM office_hours_participants WHERE user_id=?",account);
        jdbc.update("DELETE FROM voice_channel_presences WHERE member_user_id=?",account);
        jdbc.update("DELETE FROM course_voice_channel_presences WHERE member_user_id=?",account);
        jdbc.update("DELETE FROM study_server_invitations WHERE invited_user_id=? OR invited_by_user_id=?",account,account);
        jdbc.update("DELETE FROM cohort_invitations WHERE invited_user_id=? OR invited_by_user_id=?",account,account);
        jdbc.update("DELETE FROM cohort_enrollments WHERE learner_user_id=?",account);
        jdbc.update("UPDATE cohort_enrollments SET enrolled_by_user_id=NULL WHERE enrolled_by_user_id=?",account);
        jdbc.update("UPDATE cohort_enrollments SET assigned_ta_user_id=NULL WHERE assigned_ta_user_id=?",account);
        jdbc.update("DELETE FROM cohort_roles WHERE user_id=?",account);
        // A shared course remains under its current server owner. Never infer a post-backup ownership transfer.
        jdbc.update("""
            INSERT INTO lifecycle_retained_courses(account_id,course_id,successor_id)
            SELECT ?,c.id,s.owner_user_id FROM courses c JOIN study_servers s ON s.id=c.study_server_id
            WHERE c.instructor_user_id=? AND s.owner_user_id<>?
              AND NOT EXISTS(SELECT 1 FROM lifecycle_terminal_targets t WHERE t.target_kind='ACCOUNT' AND t.target_id=s.owner_user_id)
              AND NOT EXISTS(SELECT 1 FROM lifecycle_retained_courses r WHERE r.account_id=? AND r.course_id=c.id)
            """,account,account,account,account);
        jdbc.update("""
            UPDATE courses SET instructor_user_id=(SELECT successor_id FROM lifecycle_retained_courses r WHERE r.account_id=? AND r.course_id=courses.id)
            WHERE instructor_user_id=? AND id IN (SELECT course_id FROM lifecycle_retained_courses WHERE account_id=?)
            """,account,account,account);
        jdbc.update("""
            INSERT INTO course_roles(course_id,user_id,role)
            SELECT r.course_id,r.successor_id,'INSTRUCTOR' FROM lifecycle_retained_courses r JOIN courses c ON c.id=r.course_id
            WHERE r.account_id=? AND c.instructor_user_id=r.successor_id
              AND NOT EXISTS(SELECT 1 FROM course_roles old WHERE old.course_id=r.course_id AND old.user_id=r.successor_id AND old.role='INSTRUCTOR')
            """,account);
        jdbc.update("DELETE FROM course_roles WHERE user_id=? AND course_id NOT IN (SELECT id FROM courses WHERE instructor_user_id=?)",account,account);
        jdbc.update("DELETE FROM study_server_roles WHERE user_id=? AND study_server_id NOT IN (SELECT id FROM study_servers WHERE owner_user_id=?)",account,account);
    }

    public com.chanter.common.lifecycle.TerminalReapplyStore.Cleanup disposition(UUID account) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM study_servers WHERE owner_user_id=?",Long.class,account)>0
                || jdbc.queryForObject("SELECT COUNT(*) FROM courses WHERE instructor_user_id=?",Long.class,account)>0)
            return com.chanter.common.lifecycle.TerminalReapplyStore.Cleanup.PENDING;
        return jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_retained_courses WHERE account_id=?",Long.class,account)>0
                ? com.chanter.common.lifecycle.TerminalReapplyStore.Cleanup.PRESERVED
                : com.chanter.common.lifecycle.TerminalReapplyStore.Cleanup.COMPLETE;
    }

    private void retain(TerminalJournal.Entry entry,String kind,String query) {
        jdbc.update("""
            INSERT INTO lifecycle_erased_content(target_kind,target_id,revision,event_id,terminal_digest,source_kind,source_id)
            SELECT ?,?,?,?,?,?,q.id FROM (%s) q
            WHERE NOT EXISTS(SELECT 1 FROM lifecycle_erased_content old WHERE old.target_kind=? AND old.target_id=? AND old.source_kind=? AND old.source_id=q.id)
            """.formatted(query),entry.targetKind(),entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),kind,
                entry.targetId(),entry.targetKind(),entry.targetId(),kind);
    }
}
