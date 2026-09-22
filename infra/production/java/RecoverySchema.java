import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;

/** Validate packaged migration checksums against a restored database without running migrations. */
public final class RecoverySchema {
    public static void main(String[] args) {
        var deadline = new Thread(() -> {
            try { Thread.sleep(30000); } catch (InterruptedException ignored) { return; }
            Runtime.getRuntime().halt(1);
        });
        deadline.setDaemon(true);
        deadline.start();
        try {
            if (args.length != 0 || !"true".equals(System.getenv("CHANTER_RECOVERY_MODE"))) throw new IllegalStateException();
            String database = required("POSTGRES_DB"), user = required("POSTGRES_USER");
            if (!database.matches("chanter_(auth|community|message|media|agent|notification|search)") || !database.equals(user))
                throw new IllegalStateException();
            var flyway = configuration().dataSource("jdbc:postgresql://postgres:5432/" + database + "?connectTimeout=5&socketTimeout=10",
                            user, required("POSTGRES_PASSWORD")).load();
            flyway.validate();
            var migrations = flyway.info().all();
            if (migrations.length == 0) throw new IllegalStateException();
            for (var migration : migrations) if (migration.getState() != MigrationState.SUCCESS) throw new IllegalStateException();
            System.out.println("RECOVERY_SCHEMA_VERIFIED");
        } catch (Exception ignored) {
            System.err.println("Restored source schema verification failed");
            System.exit(1);
        }
    }

    static org.flywaydb.core.api.configuration.FluentConfiguration configuration() {
        return Flyway.configure().locations("classpath:db/migration", "classpath:db/migration-postgresql")
                .cleanDisabled(true).outOfOrder(false).validateMigrationNaming(true)
                .ignoreMigrationPatterns(new String[0]).loggers(new String[0]).connectRetries(0)
                .initSql("SET default_transaction_read_only=on; SET statement_timeout='10s'; SET lock_timeout='2s'");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException();
        return value;
    }
}
