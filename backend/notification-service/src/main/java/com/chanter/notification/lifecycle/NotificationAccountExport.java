package com.chanter.notification.lifecycle;

import com.chanter.common.lifecycle.AccountExportProjection;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.chanter.common.lifecycle.JdbcExportRows;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationAccountExport implements AccountExportProjection {
    private final JdbcTemplate jdbc;
    public NotificationAccountExport(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void capture(UUID account, ExportSnapshotStore.Capture output) throws IOException {
        JdbcExportRows.write(jdbc, output, "notification_metadata", """
            SELECT id,user_id,kind,filter_bucket,source_type,source_id,study_server_id,course_id,cohort_id,channel_id,created_at,read_at,done_at
            FROM notifications WHERE user_id=? ORDER BY created_at,id
            """, account);
        output.jsonLines("coverage", rows -> rows.add(Map.of("source", "notification", "personalMetadata", "Addressed notifications and read/done state are included.",
                "omissions", List.of("Source titles, body previews, labels and links require current source authorization and are excluded from this metadata section.",
                "Canonical content is exported by its owning source service."))));
    }
}
