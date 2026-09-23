package com.chanter.media.lifecycle;

import com.chanter.common.lifecycle.AccountExportProjection;
import com.chanter.common.lifecycle.ExportSnapshotAccess;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.chanter.common.lifecycle.JdbcExportRows;
import com.chanter.media.application.CourseResourceService;
import com.chanter.media.domain.CourseResource;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MediaAccountExport implements AccountExportProjection, ExportSnapshotAccess {
    private final JdbcTemplate jdbc;
    private final CourseResourceService resources;
    public MediaAccountExport(JdbcTemplate jdbc, CourseResourceService resources) { this.jdbc = jdbc; this.resources = resources; }
    @Override public void capture(UUID account, ExportSnapshotStore.Capture output) throws IOException {
        JdbcExportRows.write(jdbc, output, "uploaded_file_metadata", """
            SELECT id,course_id,title,file_name,content_type,byte_size,ai_approved,uploaded_by_user_id,created_at,state,sha256,
                ingestion_status,ingestion_signals,study_server_id,updated_at
            FROM course_resources WHERE uploaded_by_user_id=? ORDER BY created_at,id
            """, account);
        var omitted = new java.util.ArrayList<Map<String,Object>>(); UUID after = new UUID(0, 0);
        while (true) {
            output.checkBudget();
            var ids = jdbc.query("SELECT id FROM course_resources WHERE uploaded_by_user_id=? AND id>? ORDER BY id LIMIT 100",
                    (rs, row) -> rs.getObject(1, UUID.class), account, after);
            if (ids.isEmpty()) break;
            for (UUID id : ids) {
                output.checkBudget();
                CourseResource resource;
                CourseResourceService.StoredCourseResourceContent downloaded;
                try {
                    resource = current(account, id);
                    downloaded = resources.downloadCourseResource(id, account);
                } catch (ResponseStatusException denied) {
                    if (!java.util.Set.of(403,404,409,410).contains(denied.getStatusCode().value())) throw denied;
                    if (omitted.size() >= 10_000) throw new ExportSnapshotStore.ExportFailure("EXPORT_PROTECTED_ITEM_LIMIT", null);
                    omitted.add(Map.of("resourceId", id, "reason", "CLEAN_FILE_OR_CURRENT_ACCESS_UNAVAILABLE"));
                    continue;
                }
                // Revocation after bytes were captured must roll the snapshot back, not claim those bytes were omitted.
                try (var input = downloaded.content()) {
                    if (!downloaded.courseResource().id().equals(id) || !resource.sha256().equals(downloaded.courseResource().sha256())) throw denied();
                    output.file(id, input);
                    require(account, new ExportSnapshotStore.AccessScope("RESOURCE", id, resource.sha256()));
                }
            }
            after = ids.getLast();
        }
        output.jsonLines("unavailable_files", rows -> { for (var row : omitted) rows.add(row); });
        output.jsonLines("coverage", rows -> rows.add(Map.of("source", "media", "files", "Uploaded metadata and currently authorized, verified AVAILABLE file bytes.",
                "omissions", List.of("Quarantined, rejected, missing, deleting or inaccessible file bytes are not exported.",
                "Storage keys, migration locations and storage credentials are excluded."))));
    }
    @Override public void require(UUID account, ExportSnapshotStore.AccessScope scope) {
        if (!scope.kind().equals("RESOURCE") || scope.expectedDigest() == null) throw denied();
        if (!scope.expectedDigest().equals(current(account, scope.id()).sha256())) throw denied();
    }
    private CourseResource current(UUID account, UUID id) {
        var resource = resources.getCourseResource(id, account);
        if (!resource.id().equals(id) || !account.equals(resource.uploadedByUserId()) || !resource.state().equals("AVAILABLE") || resource.sha256() == null)
            throw denied();
        return resource;
    }
    private static ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "EXPORT_FILE_ACCESS_REVOKED"); }
}
