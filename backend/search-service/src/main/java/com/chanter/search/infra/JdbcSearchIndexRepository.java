package com.chanter.search.infra;

import com.chanter.search.domain.SearchDocumentType;
import com.chanter.search.domain.SearchHit;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcSearchIndexRepository {

    private static final String INSERT_ENTRY_SQL = """
            INSERT INTO search_index_entries (
                id, study_server_id, course_id, course_title, document_type,
                source_id, title, body_text, indexed_at
            ) SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?
            WHERE NOT EXISTS (SELECT 1 FROM durable_event_cursor WHERE aggregate_key=?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSearchIndexRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void replaceStudyServerIndex(UUID studyServerId, List<IndexEntry> entries) {
        jdbcTemplate.queryForObject("SELECT id FROM durable_consumer_lock WHERE id=1 FOR UPDATE", Integer.class);
        jdbcTemplate.update("""
            DELETE FROM search_index_entries s WHERE study_server_id = ? AND NOT EXISTS
            (SELECT 1 FROM durable_event_cursor c WHERE c.aggregate_key=CONCAT(s.document_type, ':', CAST(s.source_id AS VARCHAR)))
            """, studyServerId);

        if (entries.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(INSERT_ENTRY_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement preparedStatement, int index) throws SQLException {
                IndexEntry entry = entries.get(index);
                preparedStatement.setObject(1, entry.id());
                preparedStatement.setObject(2, entry.studyServerId());
                preparedStatement.setObject(3, entry.courseId());
                preparedStatement.setString(4, entry.courseTitle());
                preparedStatement.setString(5, entry.documentType().name());
                preparedStatement.setObject(6, entry.sourceId());
                preparedStatement.setString(7, entry.title());
                preparedStatement.setString(8, entry.bodyText());
                preparedStatement.setTimestamp(9, Timestamp.from(entry.indexedAt()));
                preparedStatement.setString(10, entry.documentType().name() + ":" + entry.sourceId());
            }

            @Override
            public int getBatchSize() {
                return entries.size();
            }
        });
    }

    public List<SearchHit> search(
            UUID studyServerId,
            List<UUID> visibleCourseIds,
            String query,
            int limit,
            SearchDocumentType type,
            UUID courseId,
            SearchHit after
    ) {
        String trimmedQuery = query.trim();
        if (trimmedQuery.isEmpty()) {
            return List.of();
        }

        String pattern = likePattern(trimmedQuery);
        String placeholders = visibleCourseIds.isEmpty() ? "NULL" : String.join(",", visibleCourseIds.stream().map(id -> "?").toList());
        var args = new java.util.ArrayList<Object>();
        args.add(studyServerId);
        args.addAll(visibleCourseIds);
        args.add(pattern);
        args.add(pattern);
        var sql = new StringBuilder("""
                SELECT document_type, course_id, course_title, source_id, title, body_text, href, channel_id, channel_scope
                FROM search_index_entries
                WHERE (study_server_id = ? OR study_server_id IS NULL)
                  AND (course_id IS NULL OR course_id IN (%s))
                  AND (
                    LOWER(title) LIKE ? ESCAPE '\\'
                    OR LOWER(body_text) LIKE ? ESCAPE '\\'
                  )
                """.formatted(placeholders));
        if (type != null) { sql.append(" AND document_type=?"); args.add(type.name()); }
        if (courseId != null) { sql.append(" AND course_id=?"); args.add(courseId); }
        if (after != null) {
            sql.append(" AND (title>? OR (title=? AND (document_type>? OR (document_type=? AND source_id>?))))");
            args.add(after.title());
            args.add(after.title());
            args.add(after.documentType().name());
            args.add(after.documentType().name());
            args.add(after.sourceId());
        }
        sql.append(" ORDER BY title, document_type, source_id LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), (resultSet, rowNum) -> mapHit(resultSet), args.toArray());
    }

    private static String likePattern(String query) {
        String escaped = query.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private SearchHit mapHit(ResultSet resultSet) throws SQLException {
        String bodyText = resultSet.getString("body_text");
        String title = resultSet.getString("title");
        return new SearchHit(
                SearchDocumentType.valueOf(resultSet.getString("document_type")),
                resultSet.getObject("course_id", UUID.class),
                resultSet.getString("course_title"),
                UUID.fromString(resultSet.getString("source_id")),
                title,
                snippet(title, bodyText), resultSet.getString("href"), resultSet.getObject("channel_id", UUID.class), resultSet.getString("channel_scope")
        );
    }

    public void apply(com.chanter.common.events.SearchChange change) {
        jdbcTemplate.update("DELETE FROM search_index_entries WHERE document_type=? AND source_id=?", change.type(), change.sourceId());
        if (!change.deleted()) jdbcTemplate.update("""
            INSERT INTO search_index_entries (id, study_server_id, course_id, course_title, document_type, source_id,
                title, body_text, indexed_at, href, channel_id, channel_scope) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), change.studyServerId(), change.courseId(), "", change.type(), change.sourceId(),
                truncate(change.title(), 512), truncate(change.body(), 4000), Timestamp.from(Instant.now()), change.href(), change.channelId(), change.channelScope());
    }

    private static String truncate(String value, int limit) {
        return value == null ? "" : value.substring(0, Math.min(limit, value.length()));
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
