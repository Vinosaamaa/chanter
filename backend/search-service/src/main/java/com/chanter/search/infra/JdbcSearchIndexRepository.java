package com.chanter.search.infra;

import com.chanter.search.domain.SearchDocumentType;
import com.chanter.search.domain.SearchHit;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSearchIndexRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSearchIndexRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replaceStudyServerIndex(UUID studyServerId, List<IndexEntry> entries) {
        jdbcTemplate.update("DELETE FROM search_index_entries WHERE study_server_id = ?", studyServerId);

        for (IndexEntry entry : entries) {
            jdbcTemplate.update(
                    """
                    INSERT INTO search_index_entries (
                        id, study_server_id, course_id, course_title, document_type,
                        source_id, title, body_text, indexed_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    entry.id(),
                    entry.studyServerId(),
                    entry.courseId(),
                    entry.courseTitle(),
                    entry.documentType().name(),
                    entry.sourceId(),
                    entry.title(),
                    entry.bodyText(),
                    Timestamp.from(entry.indexedAt())
            );
        }
    }

    public List<SearchHit> search(
            UUID studyServerId,
            List<UUID> visibleCourseIds,
            String query,
            int limit
    ) {
        if (visibleCourseIds.isEmpty()) {
            return List.of();
        }

        String trimmedQuery = query.trim();
        if (trimmedQuery.isEmpty()) {
            return List.of();
        }

        String pattern = "%" + trimmedQuery.toLowerCase() + "%";
        String placeholders = String.join(",", visibleCourseIds.stream().map(id -> "?").toList());
        Object[] args = new Object[visibleCourseIds.size() + 4];
        args[0] = studyServerId;
        for (int index = 0; index < visibleCourseIds.size(); index++) {
            args[index + 1] = visibleCourseIds.get(index);
        }
        args[visibleCourseIds.size() + 1] = pattern;
        args[visibleCourseIds.size() + 2] = pattern;
        args[visibleCourseIds.size() + 3] = limit;

        return jdbcTemplate.query(
                """
                SELECT document_type, course_id, course_title, source_id, title, body_text
                FROM search_index_entries
                WHERE study_server_id = ?
                  AND course_id IN (%s)
                  AND (
                    LOWER(title) LIKE ?
                    OR LOWER(body_text) LIKE ?
                  )
                ORDER BY title
                LIMIT ?
                """.formatted(placeholders),
                (resultSet, rowNum) -> mapHit(resultSet),
                args
        );
    }

    private SearchHit mapHit(ResultSet resultSet) throws SQLException {
        String bodyText = resultSet.getString("body_text");
        String title = resultSet.getString("title");
        return new SearchHit(
                SearchDocumentType.valueOf(resultSet.getString("document_type")),
                UUID.fromString(resultSet.getString("course_id")),
                resultSet.getString("course_title"),
                UUID.fromString(resultSet.getString("source_id")),
                title,
                snippet(title, bodyText)
        );
    }

    private String snippet(String title, String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            return title;
        }
        return bodyText.length() > 160 ? bodyText.substring(0, 157) + "..." : bodyText;
    }

    public record IndexEntry(
            UUID id,
            UUID studyServerId,
            UUID courseId,
            String courseTitle,
            SearchDocumentType documentType,
            UUID sourceId,
            String title,
            String bodyText,
            Instant indexedAt
    ) {
    }
}
