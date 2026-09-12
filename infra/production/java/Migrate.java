import org.flywaydb.core.Flyway;

/** One-shot migration job. Normal service containers always disable Flyway. */
public final class Migrate {
    public static void main(String[] args) {
        String database = required("POSTGRES_DB");
        if (!database.matches("chanter_[a-z]+")) throw new IllegalArgumentException("Invalid database name");
        Flyway.configure().dataSource("jdbc:postgresql://postgres:5432/" + database,
                        required("POSTGRES_USER"), required("POSTGRES_PASSWORD"))
                .locations("classpath:db/migration", "classpath:db/migration-postgresql").cleanDisabled(true).outOfOrder(false)
                .validateOnMigrate(true).load().migrate();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }
}
