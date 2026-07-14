package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V12_1__drop_legacy_course_channel_name_constraint extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        List<String> matchingConstraints = findLegacyConstraints(connection);
        if (matchingConstraints.size() != 1) {
            throw new SQLException(
                    "Expected one legacy course_channels(course_id, name) constraint, found "
                            + matchingConstraints.size()
            );
        }

        String quote = connection.getMetaData().getIdentifierQuoteString();
        String constraintName = quoteIdentifier(matchingConstraints.getFirst(), quote);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE course_channels DROP CONSTRAINT " + constraintName);
        }
    }

    private static List<String> findLegacyConstraints(Connection connection) throws SQLException {
        String query = """
                SELECT tc.constraint_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_catalog = tc.constraint_catalog
                 AND kcu.constraint_schema = tc.constraint_schema
                 AND kcu.constraint_name = tc.constraint_name
                WHERE LOWER(tc.table_schema) = LOWER(?)
                  AND LOWER(tc.table_name) = 'course_channels'
                  AND UPPER(tc.constraint_type) = 'UNIQUE'
                GROUP BY tc.constraint_name
                HAVING COUNT(*) = 2
                   AND SUM(CASE WHEN LOWER(kcu.column_name) IN ('course_id', 'name') THEN 1 ELSE 0 END) = 2
                """;
        List<String> constraints = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, connection.getSchema());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    constraints.add(resultSet.getString(1));
                }
            }
        }
        return constraints;
    }

    private static String quoteIdentifier(String identifier, String quote) {
        String safeQuote = quote == null ? "" : quote.trim();
        if (safeQuote.isEmpty()) {
            return identifier;
        }
        return safeQuote + identifier.replace(safeQuote, safeQuote + safeQuote) + safeQuote;
    }
}
