/** No database or listener: exercise the actual pinned Flyway configuration and diagnostic boundary. */
public final class RecoverySchemaContractTest {
    public static void main(String[] args) {
        var configuration = RecoverySchema.configuration();
        org.flywaydb.core.api.logging.LogFactory.setConfiguration(configuration);
        org.flywaydb.core.api.logging.LogFactory.getLog(RecoverySchemaContractTest.class).error("PRIVATE_SCHEMA_ERROR_CANARY");
        if (!configuration.isCleanDisabled() || configuration.isOutOfOrder()
                || configuration.getIgnoreMigrationPatterns().length != 0
                || !configuration.getInitSql().contains("default_transaction_read_only=on")) throw new AssertionError();
        System.out.println("RECOVERY_SCHEMA_CONFIG_VERIFIED");
    }
}
