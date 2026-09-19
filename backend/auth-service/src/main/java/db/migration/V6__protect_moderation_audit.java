package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** PostgreSQL is the production authority. H2 tests exercise application behavior separately. */
public class V6__protect_moderation_audit extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        if (!context.getConnection().getMetaData().getDatabaseProductName().equals("PostgreSQL")) return;
        try (var statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE FUNCTION reject_moderation_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        RAISE EXCEPTION 'Moderation history is append-only';
                    END;
                    $$
                    """);
            statement.execute("""
                    CREATE TRIGGER moderation_audit_immutable BEFORE UPDATE OR DELETE OR TRUNCATE
                    ON moderation_audit FOR EACH STATEMENT EXECUTE FUNCTION reject_moderation_history_mutation()
                    """);
            statement.execute("""
                    CREATE TRIGGER moderation_notes_immutable BEFORE UPDATE OR DELETE OR TRUNCATE
                    ON moderation_notes FOR EACH STATEMENT EXECUTE FUNCTION reject_moderation_history_mutation()
                    """);
        }
    }
}
