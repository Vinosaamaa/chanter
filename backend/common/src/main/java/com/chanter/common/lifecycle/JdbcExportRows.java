package com.chanter.common.lifecycle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

/** Streams source-defined SELECT projections without retaining an account-sized result list. */
public final class JdbcExportRows {
    private JdbcExportRows() { }

    public static void write(JdbcTemplate jdbc, ExportSnapshotStore.Capture output, String section, String sql, Object... parameters) throws IOException {
        try {
            output.jsonLines(section, rows -> jdbc.query(connection -> {
                var statement = connection.prepareStatement(sql);
                statement.setFetchSize(128);
                for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
                return statement;
            }, (RowCallbackHandler) result -> {
                var data = new LinkedHashMap<String, Object>();
                var metadata = result.getMetaData();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    Object value = result.getObject(index);
                    if (value instanceof java.sql.Timestamp timestamp) value = timestamp.toInstant().toString();
                    else if (value instanceof java.time.temporal.TemporalAccessor || value instanceof java.util.UUID) value = value.toString();
                    else if (value != null && !(value instanceof String || value instanceof Number || value instanceof Boolean))
                        throw new IllegalArgumentException("Unsupported export field type");
                    data.put(metadata.getColumnLabel(index).toLowerCase(java.util.Locale.ROOT), value);
                }
                try { rows.add(data); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            }));
        } catch (UncheckedIOException failure) { throw failure.getCause(); }
    }
}
