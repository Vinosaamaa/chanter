package com.chanter.message.lifecycle;

import com.chanter.common.lifecycle.AccountExportProjection;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.chanter.common.lifecycle.JdbcExportRows;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Authored text and personal relationship metadata do not silently include another person's content. */
@Component
public class MessageAccountExport implements AccountExportProjection {
    private final JdbcTemplate jdbc;
    public MessageAccountExport(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void capture(UUID account, ExportSnapshotStore.Capture output) throws IOException {
        rows(output, "authored_channel_messages", "SELECT id,channel_id,sender_user_id,body,created_at FROM channel_messages WHERE sender_user_id=? ORDER BY created_at,id", account);
        rows(output, "authored_direct_messages", "SELECT id,sender_user_id,recipient_user_id,body,sent_at FROM direct_messages WHERE sender_user_id=? ORDER BY sent_at,id", account);
        rows(output, "received_message_metadata", "SELECT id,sender_user_id,recipient_user_id,sent_at FROM direct_messages WHERE recipient_user_id=? ORDER BY sent_at,id", account);
        rows(output, "friend_requests", "SELECT id,sender_user_id,recipient_user_id,status,created_at FROM friend_requests WHERE sender_user_id=? OR recipient_user_id=? ORDER BY created_at,id", account, account);
        rows(output, "blocks", "SELECT blocker_user_id,blocked_user_id,created_at FROM user_blocks WHERE blocker_user_id=? ORDER BY created_at,blocked_user_id", account);
        rows(output, "support_questions", "SELECT id,channel_message_id,channel_id,sender_user_id,body,status,created_at FROM support_questions WHERE sender_user_id=? ORDER BY created_at,id", account);
        rows(output, "authored_support_replies", "SELECT id,support_question_id,author_user_id,body,created_at FROM support_question_replies WHERE author_user_id=? ORDER BY created_at,id", account);
        rows(output, "faq_approval_metadata", "SELECT id,course_id,approved_by_user_id,created_at,updated_at FROM approved_faqs WHERE approved_by_user_id=? ORDER BY created_at,id", account);
        rows(output, "own_ta_queue", "SELECT id,cohort_id,support_question_id,channel_id,learner_user_id,body,status,assigned_ta_user_id,created_at,updated_at FROM ta_queue_items WHERE learner_user_id=? ORDER BY created_at,id", account);
        rows(output, "ta_assignment_metadata", "SELECT id,cohort_id,support_question_id,channel_id,assigned_ta_user_id,status,created_at,updated_at FROM ta_queue_items WHERE assigned_ta_user_id=? ORDER BY created_at,id", account);
        output.jsonLines("coverage", records -> records.add(Map.of("source", "message", "authoredText", "Included for this account.",
                "omissions", List.of("Received message bodies require the current participant and moderation export guard.",
                "FAQ approval and TA assignment do not imply authorship of another person's question or answer.",
                "Other people's blocks, private content, idempotency keys and operator evidence are excluded."))));
    }
    private void rows(ExportSnapshotStore.Capture output, String section, String sql, Object... parameters) throws IOException {
        JdbcExportRows.write(jdbc, output, section, sql, parameters);
    }
}
